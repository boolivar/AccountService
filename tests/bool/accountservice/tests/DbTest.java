package bool.accountservice.tests;

import static bool.server.AccountService.Db;
import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DbTest {

	Db db;
	
	@Before
	public void setUp() throws Exception {
		db = new Db("jdbc:oracle:thin:@localhost:1521/XE", "username", "password");
		db.clear();
	}

	@After
	public void tearDown() throws Exception {
		db.clear();
		db = null;
	}

	@Test
	public final void testDb() {
		
	}

	@Test
	public final void testRead() throws SQLException {
		for (int i = 0; i < 10; ++i) {
			assertEquals(0, db.read(1));
		}
	}
	
	@Test
	public final void testInsert() throws SQLException {
		for (int i = 0; i < 10; ++i) {
			assertEquals(1, db.insert(i, i*5));
			assertEquals(i*5, db.read(i));
		}
	}

	@Test
	public final void testUpdate() throws SQLException {
		for (int i = 0; i < 10; ++i) {
			assertEquals(1, db.insert(i, i));
			assertEquals(i, db.read(i));
			
			assertEquals(1, db.update(i, 0));
			assertEquals(0, db.read(i));
		}
	}
}
