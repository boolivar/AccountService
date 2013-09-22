package bool.server;

import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AccountService implements RemoteAccountService {
	public static final String URL = "jdbc:oracle:thin:@";
	public static String host = "localhost:1521/XE";
	
	private static String username = "username";
	private static String password = "password";
	
	private static final String HELP_STRING
		= "USAGE:\n"
		+ "	--host [url]/SID    set server address and sid, default: " + host + "\n"
		+ "	--username user     set username for database connection, default: " + username + "\n"
		+ "	--password pass     set password for database connection, default: " + password + "\n";
	
	private final ConcurrentHashMap<Integer, AccountHandler> cachingHandlers = new ConcurrentHashMap<Integer, AccountHandler>();
	
	private final AtomicInteger readerRequestsCounter = new AtomicInteger(0);
	private final AtomicInteger writerRequestsCounter = new AtomicInteger(0);
	
	private final Db db;
	private final Stat stat;
	
	public static void main(String[] args) {
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
		
		try {
			if (!parseArgs(args)) {
				System.out.println(HELP_STRING);
				return;
			}
			
			System.out.println("URL: " + URL + host);
			System.out.println("username: " + username);
			
			AccountService service = new AccountService(URL + host, username, password);
			RemoteAccountService accountServiceStub = (RemoteAccountService) UnicastRemoteObject.exportObject(service, 1234);
			RemoteStatService statServiceStub = (RemoteStatService) UnicastRemoteObject.exportObject(service.getStatService(), 1234);
			
			Registry registry = LocateRegistry.createRegistry(1234);
			registry.bind(RemoteAccountService.REGISTRY_LOOKUP_NAME, accountServiceStub);
			registry.bind(RemoteStatService.REGISTRY_LOOKUP_NAME, statServiceStub);
			
			System.out.println("Service running...");
			while (!Thread.interrupted()) {
				Thread.sleep(1000);
				service.collectData();
			}
			System.out.println("Service stopped");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static boolean parseArgs(String[] args) {
		int i = 0;
		while (i < args.length) {
			if ("--username".equals(args[i])) {
				++i;
				if (i < args.length) {
					username = args[i];
				}
			}else if ("--password".equals(args[i])) {
				++i;
				if (i < args.length) {
					password = args[i];
				}
			}else if ("--host".equals(args[i])) {
				++i;
				host = args[i];
			}else {
				return false;
			}
			++i;
		}
		
		return true;
	}
	
	public AccountService(String url, String username, String password) throws SQLException {
		db = new Db(url, username, password);
		stat = new Stat();
	}
	
	@Override
	public Long getAmount(Integer id) throws RemoteException {
		readerRequestsCounter.incrementAndGet();
		
		try {
			AccountHandler handler = getAccountHandler(id);
			return handler.get();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RemoteException("Internal database error");
		}
	}

	@Override
	public void addAmount(Integer id, Long value) throws RemoteException {
		writerRequestsCounter.incrementAndGet();
		
		try {
			AccountHandler handler = getAccountHandler(id);
			handler.increment(value);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RemoteException("Internal database error");
		}
	}
	
	public void reset() {
		stat.reset();
	}
	
	public String stat() {
		return stat.stat();
	}
	
	public void collectData() {
		stat.collectData();
	}
	
	public RemoteStatService getStatService() {
		return stat;
	}
	
	private AccountHandler getAccountHandler(int id) throws SQLException {
		AccountHandler handler = cachingHandlers.get(id);
		if (handler == null) {
			handler = new AccountHandler(id);
			AccountHandler fasterCreatedHandler = cachingHandlers.putIfAbsent(id, handler);
			if (fasterCreatedHandler != null) {
				handler = fasterCreatedHandler;
			}
		}
		return handler;
	}
	
	private class AccountHandler {
		private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		private final ReentrantReadWriteLock.WriteLock wLock = lock.writeLock();
		private final ReentrantReadWriteLock.ReadLock rLock = lock.readLock();
		
		private int id;
		private long amount;
		
		public AccountHandler(int id) throws SQLException {
			this.id = id;
			this.amount = db.read(id);
		}
		
		public long get() {
			rLock.lock();
			try {
				return amount;
			} finally {
				rLock.unlock();
			}
		}
		
		public void increment(long value) throws SQLException {
			wLock.lock();
			try {
				amount += value;
				if (db.update(id, amount) == 0) {
					db.insert(id, amount);
				}
			} finally {
				wLock.unlock();
			}
		}
	}
	
	public static class Db {
		private final String url;
		private final String user;
		private final String password;
		
		private static final String tableName = "account_service_table";
		
		private static final String selectUserTablesSql = "SELECT table_name FROM user_tables";
		private static final String createTableSql = String.format("CREATE TABLE %s (id integer, value integer)", tableName);
		private static final String truncateSql = "TRUNCATE TABLE " + tableName;
		private static final String insertIdValueSql = String.format("INSERT INTO %s (id, value) VALUES (?,?)", tableName);
		private static final String updateValueIdSql = String.format("UPDATE %s SET value=? WHERE id=?", tableName);
		private static final String selectValueSql = String.format("SELECT value FROM %s WHERE id=?", tableName);
		
		public Db(String url, String user, String password) throws SQLException {
			this.url = url;
			this.user = user;
			this.password = password;
			initDb();
		}
		
		public void clear() throws SQLException {
			Connection connection = getConnection();
			try {
				PreparedStatement query = connection.prepareStatement(truncateSql);
				query.executeUpdate();
			} finally {
				connection.close();
			}
		}
		
		public int insert(int id, long value) throws SQLException {
			Connection connection = getConnection();
			try {
				PreparedStatement query = connection.prepareStatement(insertIdValueSql);
				query.setInt(1, id);
				query.setString(2, String.valueOf(value));
				return query.executeUpdate();
			} finally {
				connection.close();
			}
		}
		
		public int update(int id, long value) throws SQLException {
			Connection connection = getConnection();
			try {
				PreparedStatement query = connection.prepareStatement(updateValueIdSql);
				query.setString(1, String.valueOf(value));
				query.setInt(2, id);
				return query.executeUpdate();
			} finally {
				connection.close();
			}
		}
		
		public long read(int key) throws SQLException {
			Connection connection = getConnection();
			try {
				PreparedStatement query = connection.prepareStatement(selectValueSql);
				query.setInt(1, key);
				ResultSet result = query.executeQuery();
				if (result.next()) {
					return result.getLong(1);
				}
				return 0;
			} finally {
				connection.close();
			}
		}
		
		private void initDb() throws SQLException {
			Connection connection = getConnection();
			try {
				PreparedStatement query = connection.prepareStatement(selectUserTablesSql);
				ResultSet records = query.executeQuery();
				boolean tableFound = false;
				while (records.next()) {
					String table = records.getString(1);
					if (table != null && tableName.equals(table.toLowerCase())) {
						tableFound = true;
						break;
					}
				}
				query.close();
				
				if (!tableFound) {
					query = connection.prepareStatement(createTableSql);
					query.execute();
					System.out.println("User table " + tableName + " created");
				}
			} finally {
				connection.close();
			}
		}
		
		private Connection getConnection() throws SQLException {
			return DriverManager.getConnection(url, user, password);
		}
	}

	private class Stat implements RemoteStatService {
		private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		private final ReentrantReadWriteLock.ReadLock rLock = lock.readLock();
		private final ReentrantReadWriteLock.WriteLock wLock = lock.writeLock();
		
		private long prevTime = System.currentTimeMillis();
		
		private int prevReadRequests = 0;
		private int prevWriteRequests = 0;

		private int readRequests;
		private int writeRequests;
		private int readersPerSecond;
		private int writersPerSecond;
		
		public void reset() {
			writerRequestsCounter.set(0);
			readerRequestsCounter.set(0);
			
			wLock.lock();
			try {
				prevReadRequests = 0;
				prevWriteRequests = 0;
			}finally {
				wLock.unlock();
			}
		}
		
		public String stat() {
			rLock.lock();
			try {
				return String.format("readers per second:%d, writers per second:%d, total read requests:%d, total write requests:%d",
						readersPerSecond, writersPerSecond, readRequests, writeRequests);
			} finally {
				rLock.unlock();
			}
		}
		
		public void collectData() {
			wLock.lock();
			try {
				readRequests = readerRequestsCounter.get();
				writeRequests = writerRequestsCounter.get();
				
				int readRequestsDelta = readRequests - prevReadRequests;
				int writeRequestsDelta = writeRequests - prevWriteRequests;
				
				prevReadRequests = readRequests;
				prevWriteRequests = writeRequests;
				
				long nowTime = System.currentTimeMillis();
				float timeDeltaSeconds = (nowTime - prevTime)/1000f;
				prevTime = nowTime;
				
				if (timeDeltaSeconds > 0.5) {
					readersPerSecond = Math.round(readRequestsDelta/timeDeltaSeconds);
					writersPerSecond = Math.round(writeRequestsDelta/timeDeltaSeconds);
				}
			}finally {
				wLock.unlock();
			}
		}
	}
}
