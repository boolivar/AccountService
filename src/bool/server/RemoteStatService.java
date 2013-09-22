package bool.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteStatService extends Remote {
	static final String REGISTRY_LOOKUP_NAME = "StatService";
	
	void reset() throws RemoteException;
	String stat() throws RemoteException;
}
