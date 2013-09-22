package bool.accountservice.tests;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bool.client.Client;

public class IndexGeneratorTest {

	private Random rand;

	@Before
	public void setUp() throws Exception {
		rand = new Random();
		rand.setSeed(0);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testForwardIndexGenerator() {
		Iterator<Integer> g = new Client.ForwardIndexGenerator(100, -1).iterator();
		for (Integer i = 0; i < 100; ++i) {
			assertTrue(g.hasNext());
			assertEquals(i, g.next());
		}
		
		for (Integer i = 0; i < 100; ++i) {
			assertTrue(g.hasNext());
			assertEquals(i, g.next());
		}
		assertTrue(g.hasNext());
		
		g = new Client.ForwardIndexGenerator(10, 100).iterator();
		for (Integer i = 0; i < 100; ++i) {
			assertTrue(g.hasNext());
			assertTrue(g.next() < 10);
		}
		assertFalse(g.hasNext());
		
		g = new Client.ForwardIndexGenerator(100, 10).iterator();
		for (Integer i = 0; i < 10; ++i) {
			assertTrue(g.hasNext());
			assertEquals(i, g.next());
		}
		assertFalse(g.hasNext());
		
		g = new Client.ForwardIndexGenerator(100, 0).iterator();
		assertFalse(g.hasNext());
		
		g = new Client.ForwardIndexGenerator(0, 100).iterator();
		assertFalse(g.hasNext());
		
		assertEquals(1, calcMeanDelta(new Client.ForwardIndexGenerator(1000, -1).iterator()));
	}
	
	@Test
	public final void testRandom() {
		Random rand2 = new Random();
		rand2.setSeed(0);
		
		for(int i = 0; i < 1000; ++i) {
			assertEquals(rand.nextInt(), rand2.nextInt());
		}
	}
	
	@Test
	public final void testRandomIndexGenerator() {
		Iterator<Integer> g = new Client.RandomIndexGenerator(10, -1).iterator();
		
		for (int i = 0; i < 1000; ++i) {
			assertTrue(g.hasNext());
			assertTrue(g.next() < 10);
		}
		
		g = new Client.RandomIndexGenerator(100, 0).iterator();
		assertFalse(g.hasNext());
		
		g = new Client.RandomIndexGenerator(0, 100).iterator();
		assertFalse(g.hasNext());
		
		assertTrue(calcMeanDelta(new Client.RandomIndexGenerator(1000, -1).iterator()) > 50);
	}
	
	private int calcMeanDelta(Iterator<Integer> generator) {
		long wholeDelta = 0;
		
		int prev = generator.next();
		
		for (int i = 1; i < 1000; ++i) {
			if (!generator.hasNext())
				break;
			int next = generator.next();
			wholeDelta += Math.abs(next - prev);
			prev = next;
		}
		
		return Math.round(wholeDelta/1000f);
	}
}
