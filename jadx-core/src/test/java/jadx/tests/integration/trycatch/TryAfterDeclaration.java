package jadx.tests.integration.trycatch;

import static jadx.tests.api.utils.JadxMatchers.containsOne;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jadx.NotYetImplemented;
import jadx.NotYetImplementedExtension;
import jadx.core.dex.nodes.ClassNode;
import jadx.tests.api.IntegrationTest;

@ExtendWith(NotYetImplementedExtension.class)
public class TryAfterDeclaration extends IntegrationTest {

	/**
	 * Issue #62.
	 */
	@Test
	@NotYetImplemented
	public void test62() {
		ClassNode cls = getClassNode(TestClass.class);
		String code = cls.getCode().toString();

		assertThat(code, containsOne("try {"));
	}
}

class TestClass {
	public static void consume() throws IOException {
		InputStream	bis = null;
		try {
			bis = new FileInputStream("1.txt");
			while (bis != null) {
				System.out.println("c");
			}
		} catch (final IOException e) {
		}
	}
}

