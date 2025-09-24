package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.io.LittleEndianDataOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.Compressor;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.io.compress.zlib.ZlibCompressor;
import org.apache.hadoop.io.compress.zlib.ZlibFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class NumpyLogger {

    String  basePath;
    int chunkCount = 0;
    int chunkSize;
    int rowCount=0;
    int rowSize = -1;
    Chunk chunk;

    public NumpyLogger(String basePath, int chunkSize)  {
        try{
        Files.createDirectories(Path.of(basePath));
        }catch(IOException x){
            throw new RuntimeException(x);
        }
        this.basePath = Path.of(basePath ,"chunk").toString();
        this.chunkSize = chunkSize;
    }

    public synchronized void log(double[] data){
        // FIXME
        if(data == null) {
            return;
        }
        if(rowSize==-1){
            rowSize = data.length;
        }
        if(data.length != rowSize){
            throw new IllegalArgumentException("input must always be the same length");
        }
        try{
            if(rowCount>=chunkSize) {
                chunk.close();
                chunk = null;
            }
        if(chunk == null){
            chunk = new Chunk(basePath + chunkCount +".zip");
            rowCount=0;
            chunkCount++;
        }
        chunk.log(data);
        rowCount++;
        }catch (IOException x){
            throw new RuntimeException(x);
        }
    }

    public void close() throws IOException {
        if(chunk != null) {
            chunk.close();
        }
        String metaData = "rowSize:"+rowSize;
        FileWriter file = new FileWriter(basePath+"meta.txt");
        file.write(metaData);
        file.close();
    }

    public static class Chunk {
        String fileName;
        ByteArrayOutputStream bytes;
        LittleEndianDataOutputStream out;
        List<double[]> rows;

        public Chunk(String fileName) throws FileNotFoundException {
            this.fileName = fileName;
            bytes = new ByteArrayOutputStream();
            out = new LittleEndianDataOutputStream(bytes);
            rows = new ArrayList<>();
        }

        public void log(double[] data) throws IOException {
            rows.add(data);

        }

        public void close() throws IOException {

            int colLength = rows.get(0).length;
            for(int colIndex = 0; colIndex < colLength; colIndex++){
                for(double[] row : rows){
                    out.writeDouble(row[colIndex]);
                }
            }
            out.close();
            rows = null;

            byte[] data = bytes.toByteArray();

            FileOutputStream file = new FileOutputStream(fileName);
            ZipOutputStream zip = new ZipOutputStream(file);
            ZipEntry entry = new ZipEntry("chunk");
            zip.putNextEntry(entry);
            zip.write(data);
            zip.close();

            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4Compressor lz4Compressor = factory.highCompressor(9);
//            int maxCompressedLength = compressor.maxCompressedLength(data.length);
//            byte[] compressed = new byte[maxCompressedLength];
//            int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, maxCompressedLength);
//            FileOutputStream out = new FileOutputStream(fileName+".lz4");
//            out.write(compressed, 0, compressedLength);
//            out.close();

            LZ4FrameOutputStream outStream = new LZ4FrameOutputStream(
                    new FileOutputStream(new File(fileName+".lz4")),
                    LZ4FrameOutputStream.BLOCKSIZE.SIZE_4MB,
                    -1L,
                    lz4Compressor,
                    XXHashFactory.fastestInstance().hash32(),
                    LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE
                    );
            RleEncoding encoding = new RleEncoding(data);
            outStream.write(data);
            outStream.close();

            Configuration conf = new Configuration();
            ZlibFactory.setCompressionLevel(conf, ZlibCompressor.CompressionLevel.BEST_COMPRESSION);
            ZlibFactory.setCompressionStrategy(conf, ZlibCompressor.CompressionStrategy.DEFAULT_STRATEGY);
            GzipCodec codec = new GzipCodec();
            codec.setConf(conf);
            Compressor compressor = codec.createCompressor();

            compressor.setInput(data, 0, data.length);
            compressor.finish();
            FileOutputStream out = new FileOutputStream(new File(fileName+".gzip"));
            byte[] buffer = new byte[512* 1024];
            while(!compressor.finished()){
                int length = compressor.compress(buffer, 0, buffer.length);
                out.write(buffer, 0, length);
            }
            compressor.end();
            out.close();
        }
    }

    public static class RleEncoding{
        ByteArrayOutputStream out;
        byte[] in;
        public RleEncoding(byte[] data){
            out = new ByteArrayOutputStream();
            in = data;
        }


        public byte[] encode() throws IOException {
            for(int i = 0; i < in.length;){
                i = scan(i);
            }
            return out.toByteArray();
        }

        int repeat(int start, int count) throws IOException {
            out.write(new byte[]{(byte)(128 | count), in[start]});
            return start+count;
        }

         int unique(int start, int count) throws IOException {
            out.write(new byte[]{(byte)(128 | count)});
            out.write(in, start, count);
            return start+count;
        }

        int scan(int start) throws IOException {
            if(start>=in.length-2){
                return unique(start, in.length-start);
            }
            byte current = in[start];
            int count =0;
            if(current == in[start+1] && current == in[start+2]){
                count=3;
                for(int i = start+3; i < in.length && count <= 127 && in[i]==current; i++){
                    count++;
                }
                return repeat(start, count);
            }
            int sameCount =0;
            count = 1;
            for(int i=start+1; i<in.length && count < 127 && sameCount < 3; i++){
                count++;
                byte candidate = in[i];
                if(candidate == current){
                    sameCount++;
                } else {
                    current = candidate;
                    sameCount = 1;
                }
            }
            if(sameCount >=3){
                count -=sameCount;
            }
            return unique(start, count);

        }

        // format
        // 1 bit: 1: repetition of count of the next byte, 0: unique sequence of count bytes
        // 7 bits: count
        // payload
    }

    public static void main(String[] args) throws IOException {
        NumpyLogger.Chunk chunk = new NumpyLogger.Chunk("jupyterLog/logs/testChunk2.zip");
        double[] data1 = new double[]{1,2,3,4,5};
        double[] data2 = new double[]{6,7,8,9,10};
        chunk.log(data1);
        chunk.log(data2);
        chunk.close();

        RleEncoding rle = new RleEncoding(new byte[]{0,0,0,0,0,1,2,3,3,3,4});
        byte[] result = rle.encode();
        System.out.println(Arrays.toString(result));

    }
}
