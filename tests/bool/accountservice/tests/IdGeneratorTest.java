package bool.accountservice.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bool.client.Client;
import bool.client.Client.*;

public class IdGeneratorTest {

	Random rand;
	
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testRangeIdGenerator() {		
		IdSource idSource = new RangeIdSource(0, 100);
	}
	
	public final void testListIdGenerator() {
		List<Integer> list = new ArrayList<Integer>(100);
		Iterable<Integer> ids = new RandomIndexGenerator(100, 100);
		for (int i: ids) {
			list.add(i);
		}
		
		IdSource idSource = new ListIdSource(list);
		for (int i = 0; i < list.size(); ++i) {
			assertEquals(list.get(i), idSource.get(i));
		}
	}
}
