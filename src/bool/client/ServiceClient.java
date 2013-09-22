package bool.client;

import java.rmi.RMISecurityManager;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashSet;

import bool.server.RemoteStatService;

public class ServiceClient {
	
	private static final String HELP_STRING
		= "USAGE:\n"
		+ "	--reset    clear remote statistics\n"
		+ "	--printer [msTimeout]    print statistics periodically with given timeout, default 500ms\n";
	
	private static boolean reset = false;
	private static int printer = -1;

	public static void main(String[] args) {
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
		try {
			if (parseArgs(args) == false) {
				System.out.println(HELP_STRING);
				return;
			}
			
			Registry registry = LocateRegistry.getRegistry("localhost", 1234);
			RemoteStatService stat = (RemoteStatService) registry.lookup(RemoteStatService.REGISTRY_LOOKUP_NAME);
			
			HashSet<String> argSet = new HashSet<String>(args.length);
			Collections.addAll(argSet, args);
			
			if (reset) {
				stat.reset();
			}
			
			if (printer > 0) {
				while (!Thread.interrupted()) {
					System.out.println(stat.stat());
					Thread.sleep(printer);
				}
			} else {
				System.out.println(stat.stat());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static boolean parseArgs(String[] args) {
		int i = 0;
		while (i < args.length) {
			if ("--reset".equals(args[i])) {
				reset = true;
			}else if ("--printer".equals(args[i])) {
				printer = 500;
				++i;
				if (i < args.length) {
					try {
						printer = Integer.parseInt(args[i]);
					} catch (NumberFormatException e) {
						--i;
					}
				}
			}else {
				return false;
			}
			++i;
		}
		
		return true;
	}
}
