package nz.cri.gns.NSHM.opensha.util;
import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;


public class NSHMRuptureSetBuilderPythonGateway {

	static CachedNSHMRuptureSetBuilder builder = new CachedNSHMRuptureSetBuilder();
	
	public static CachedNSHMRuptureSetBuilder getBuilder() {
		return builder;
	}
		
	public static void main(String[] args) {
		NSHMRuptureSetBuilderPythonGateway app = new NSHMRuptureSetBuilderPythonGateway();
		
		// app is now the gateway.entry_point
		GatewayServer server = new GatewayServer(app);
		server.start();
	}	

	static class CachedNSHMRuptureSetBuilder extends NSHMRuptureSetBuilder {
		SlipAlongRuptureModelRupSet ruptureSet;

		@Override
		public SlipAlongRuptureModelRupSet buildRuptureSet(String fsdFileName) throws DocumentException, IOException {
			ruptureSet = super.buildRuptureSet(fsdFileName);
			return ruptureSet;
		}
		
		public void writeRuptureSet(String rupSetFileName) throws IOException {
			File rupSetFile = new File(rupSetFileName);
			FaultSystemIO.writeRupSet(ruptureSet, rupSetFile);
		}		
	}
}

