package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.io.LittleEndianDataOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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

        public Chunk(String fileName) throws FileNotFoundException {
            this.fileName = fileName;
            bytes = new ByteArrayOutputStream();
            out = new LittleEndianDataOutputStream(bytes);
        }

        public void log(double[] data) throws IOException {
            for (double d : data) {
                out.writeDouble(d);
            }
        }

        public void close() throws IOException {
            out.close();

            byte[] data = bytes.toByteArray();

            FileOutputStream file = new FileOutputStream(fileName);
            ZipOutputStream zip = new ZipOutputStream(file);
            ZipEntry entry = new ZipEntry("chunk");
            zip.putNextEntry(entry);
            zip.write(data);
            zip.close();

//            LZ4Factory factory = LZ4Factory.fastestInstance();
//            LZ4Compressor compressor = factory.fastCompressor();
//            int maxCompressedLength = compressor.maxCompressedLength(data.length);
//            byte[] compressed = new byte[maxCompressedLength];
//            int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, maxCompressedLength);
//            FileOutputStream out = new FileOutputStream(fileName+".lz4");
//            out.write(compressed, 0, compressedLength);
//            out.close();

            LZ4FrameOutputStream outStream = new LZ4FrameOutputStream(new FileOutputStream(new File(fileName+".lz4")));
            outStream.write(data);
            outStream.close();

        }
    }

    public static void main(String[] args) throws IOException {
        NumpyLogger.Chunk chunk = new NumpyLogger.Chunk("jupyterLog/logs/testChunk2.zip");
        double[] data1 = new double[]{1,2,3,4,5};
        double[] data2 = new double[]{6,7,8,9,10};
        chunk.log(data1);
        chunk.log(data2);
        chunk.close();

    }
}
