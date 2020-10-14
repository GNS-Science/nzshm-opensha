package nz.cri.gns.NSHM.opensha.util;
import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * A py4j gateway for the NSHMRuptureSetBuilder.
 *  
 */
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

	/**
	 * Provide a little help for python clients using NSHMRuptureSetBuilder
	 * 
	 */
	static class CachedNSHMRuptureSetBuilder extends NSHMRuptureSetBuilder {
		SlipAlongRuptureModelRupSet ruptureSet;

		/**
		 * 
		 * @param permutationStrategyClass one of 'DOWNDIP', 'POINTS', 'UCERF3'
		 * @return this
		 */
		public NSHMRuptureSetBuilder setPermutationStrategy(String permutationStrategyClass) {
		 
			super.setPermutationStrategy(RupturePermutationStrategy.valueOf(permutationStrategyClass));
			return this;
		}
		
		/**
		 * Caches the results of the build
		 */
		@Override
		public SlipAlongRuptureModelRupSet buildRuptureSet(String fsdFileName) throws DocumentException, IOException {
			ruptureSet = super.buildRuptureSet(fsdFileName);
			return ruptureSet;
		}
		/**
		 * Write the cached rupture set to disk.
		 *  
		 * @param rupSetFileName
		 * @throws IOException
		 */
		public void writeRuptureSet(String rupSetFileName) throws IOException {
			File rupSetFile = new File(rupSetFileName);
			FaultSystemIO.writeRupSet(ruptureSet, rupSetFile);
		}		
	}
}

