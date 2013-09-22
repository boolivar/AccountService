package bool.client;

import java.rmi.RMISecurityManager;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import bool.server.RemoteAccountService;

public class Client {
	
	/**
	 * Id storage access interface.
	 */
	public interface IdSource  {
		Integer get(int index);
		int size();
	}
	
	private static final String HELP_STRING
		= "USAGE:\n"
		+ "	--rCount n    sets readers threads count, default = 1\n"
		+ "	--wCount n    sets writers threads count, default = 1\n"
		+ "	--id (range [A-C) or sequence (A,B,C))    sets ids, default [0-100)\n"
		+ "note that sequences are used in a random order while ranges used in linear order\n"
		+ "\n"
		+ "EXAMPLES:\n"
		+ "	Client --id 10-100 --rCount 10    creates 10 readers threads and 1 writer thread on id range [10-99]\n"
		+ "\n"
		+ "	Client --id 1, 5, 100    creates 1 reader thread and 1 writer thread on id sequence (1, 5, 100)\n";
	
	private static int rCount = 1;
	private static int wCount = 1;
	
	private static IdSource idSource = new RangeIdSource(0, 100);
	private static Iterable<Integer> iterationStrategy = new ForwardIndexGenerator(100, 100);
	
	public static void main(String[] args) {
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
		
		try {
			if (parseArgs(args) == false) {
				System.out.println(HELP_STRING);
				return;
			}
			
			System.out.printf("wCount: %d\n", wCount);
			System.out.printf("rCount: %d\n", rCount);
			System.out.println(idSource);
			
			Registry registry = LocateRegistry.getRegistry("localhost", 1234);
			final RemoteAccountService service = (RemoteAccountService) registry.lookup(RemoteAccountService.REGISTRY_LOOKUP_NAME);
			
			int threadsCount = rCount + wCount;
			final CountDownLatch latch = new CountDownLatch(threadsCount);
			
			for (int i = 0; i < rCount; ++i) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Iterator<Integer> idIterator = iterationStrategy.iterator();
							while (!Thread.interrupted() && idIterator.hasNext()) {
								service.getAmount(idSource.get(idIterator.next()));
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							latch.countDown();
						}
					}
				}).start();
			}
			
			for (int i = 0; i < wCount; ++i) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Iterator<Integer> idIterator = iterationStrategy.iterator();
							while (!Thread.interrupted() && idIterator.hasNext()) {
								service.addAmount(idSource.get(idIterator.next()), 1l);
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							latch.countDown();
						}
					}
				}).start();
			}
			
			System.out.println("Running...");
			latch.await();
			System.out.println("Finished");
		}catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static boolean parseArgs(String[] args) {
		int i = 0;
		while (i < args.length) {
			if ("--wCount".equals(args[i])) {
				++i;
				if (i < args.length) {
					wCount = Integer.valueOf(args[i]);
				}
			}else if ("--rCount".equals(args[i])) {
				++i;
				if (i < args.length) {
					rCount = Integer.valueOf(args[i]);
				}
			}else if ("--id".equals(args[i])) {
				++i;
				i += parseIds(args, i);
			}else {
				return false;
			}
			++i;
		}
		
		return true;
	}
	
	private static int parseIds(String[] args, int start) {
		String s = "";
		
		int skipArgsCounter = 0;
		for (int i = start; i < args.length; ++i) {
			if (args[i].contains("--")) {
				break;
			}
			s += args[i];
			++skipArgsCounter;
		}
		
		if (s.length() > 0) {
			if (s.contains("-")) {
				parseIdRange(s);
			}else {
				parseIdSequence(s);
			}
		}
		
		return skipArgsCounter-1;
	}
	
	private static void parseIdRange(String s) {
		String[] parts = s.split("-");
		if (parts.length == 2) {
			int begin = Integer.valueOf(parts[0]);
			int end = Integer.valueOf(parts[1]);
			
			idSource = new RangeIdSource(begin, end);
			iterationStrategy = new ForwardIndexGenerator(end-begin, -1);
		}
	}
	
	private static void parseIdSequence(String s) {
		String[] parts = s.split(",");
		List<Integer> list = new ArrayList<Integer>(parts.length);
		for(String part: parts) {
			list.add(Integer.valueOf(part));
		}
		
		idSource = new ListIdSource(list);
		iterationStrategy = new RandomIndexGenerator(list.size(), -1);
	}
	
	public static class ListIdSource implements IdSource {
		private final List<Integer> list;
		
		public ListIdSource(List<Integer> list) {
			this.list = list;
		}
		
		@Override
		public Integer get(int index) {
			return list.get(index);
		}

		@Override
		public int size() {
			return list.size();
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder("Index list:");
			for(int i: list) {
				sb.append(i).append(',');
			}
			return sb.toString();
		}
	}
	
	public static class RangeIdSource implements IdSource {
		private final int begin;
		private final int size;
		
		public RangeIdSource(int start, int end) {
			begin = start;
			size = end - start;
		}

		@Override
		public Integer get(int index) {
			return begin + index; 
		}

		@Override
		public int size() {
			return size;
		}
		
		public String toString() {
			return String.format("Index range: [%d - %d)", begin, begin + size);
		}
	}
	
	public static class RandomIndexGenerator implements Iterable<Integer> {
		private final int range;
		private final int size;
		
		public RandomIndexGenerator(int range, int size) {
			this.range = range;
			this.size = size;
		}

		@Override
		public Iterator<Integer> iterator() {
			return new Iterator<Integer>() {
				private final Random rand = new Random();
				private int size = RandomIndexGenerator.this.size;
				
				@Override
				public boolean hasNext() {
					return (size != 0) && (range > 0);
				}
				
				@Override
				public Integer next() {
					if (size > 0) {
						--size;
					}
					return rand.nextInt(range);
				}
				
				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
	
	public static class ForwardIndexGenerator implements Iterable<Integer> {
		private final int range;
		private final int size;
		
		public ForwardIndexGenerator(int range, int size) {
			this.range = range;
			this.size = size;
		}
		
		@Override
		public Iterator<Integer> iterator() {
			return new Iterator<Integer>() {
				private int size = ForwardIndexGenerator.this.size; 
				private int index = 0;
				
				@Override
				public boolean hasNext() {
					return (size != 0) && (range > 0);
				}
				
				@Override
				public Integer next() {
					if (size > 0) {
						--size;
					}
					
					if (index >= range) {
						index = 0;
					}
					
					return index++;
				}
				
				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
