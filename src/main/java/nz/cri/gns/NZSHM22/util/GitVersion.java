package nz.cri.gns.NZSHM22.util;

import java.io.*;

public class GitVersion {

    public GitVersion(){}

    public String getVersion(){
        try(InputStream in = GitVersion.class.getResourceAsStream("/tagged-version.txt")){
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            return read.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "error loading version";
    }

    public String getOpenshaGitRef(){
        try(InputStream in = GitVersion.class.getResourceAsStream("/opensha-ref.txt")){
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            return read.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "error loading opensha ref";
    }
}
