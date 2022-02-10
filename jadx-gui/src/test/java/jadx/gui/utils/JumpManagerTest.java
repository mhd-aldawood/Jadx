package jadx.gui.utils;

import com.android.tools.r8.internal.Ju;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jadx.gui.treemodel.TextNode;

import java.lang.reflect.Field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class JumpManagerTest {
	private JumpManager jm;
	Field currentPos = null;

	@BeforeEach
	public void setup() throws NoSuchFieldException {
		jm = new JumpManager();
		currentPos = JumpManager.class.
				getDeclaredField("currentPos");
		currentPos.setAccessible(true);
	}

	@Test
	public void testEmptyHistory() {
		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), nullValue());
	}

	@Test
	public void testEmptyHistory2() {
		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), nullValue());
		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), nullValue());
		assertThat(jm.getPrev(), nullValue());
	}

	@Test
	public void testOneElement() {
		jm.addPosition(makeJumpPos());

		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), nullValue());
	}

	@Test
	public void testTwoElements() {
		JumpPosition pos1 = makeJumpPos();
		jm.addPosition(pos1);
		JumpPosition pos2 = makeJumpPos();
		jm.addPosition(pos2);

		assertThat(jm.getPrev(), sameInstance(pos1));
		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), sameInstance(pos2));
		assertThat(jm.getNext(), nullValue());
	}

	@Test
	public void testNavigation() {
		JumpPosition pos1 = makeJumpPos();
		jm.addPosition(pos1);
		// 1@
		JumpPosition pos2 = makeJumpPos();
		jm.addPosition(pos2);
		// 1 - 2@
		assertThat(jm.getPrev(), sameInstance(pos1));
		// 1@ - 2
		JumpPosition pos3 = makeJumpPos();
		jm.addPosition(pos3);
		// 1 - 3@
		assertThat(jm.getNext(), nullValue());
		assertThat(jm.getPrev(), sameInstance(pos1));
		// 1@ - 3
		assertThat(jm.getNext(), sameInstance(pos3));
	}

	@Test
	public void testNavigation2() {
		JumpPosition pos1 = makeJumpPos();
		jm.addPosition(pos1);
		// 1@
		JumpPosition pos2 = makeJumpPos();
		jm.addPosition(pos2);
		// 1 - 2@
		JumpPosition pos3 = makeJumpPos();
		jm.addPosition(pos3);
		// 1 - 2 - 3@
		JumpPosition pos4 = makeJumpPos();
		jm.addPosition(pos4);
		// 1 - 2 - 3 - 4@
		assertThat(jm.getPrev(), sameInstance(pos3));
		// 1 - 2 - 3@ - 4
		assertThat(jm.getPrev(), sameInstance(pos2));
		// 1 - 2@ - 3 - 4
		JumpPosition pos5 = makeJumpPos();
		jm.addPosition(pos5);
		// 1 - 2 - 5@
		assertThat(jm.getNext(), nullValue());
		assertThat(jm.getNext(), nullValue());
		assertThat(jm.getPrev(), sameInstance(pos2));
		// 1 - 2@ - 5
		assertThat(jm.getPrev(), sameInstance(pos1));
		// 1@ - 2 - 5
		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), sameInstance(pos2));
		// 1 - 2@ - 5
		assertThat(jm.getNext(), sameInstance(pos5));
		// 1 - 2 - 5@
		assertThat(jm.getNext(), nullValue());
	}

	@Test
	public void addSame() {
		JumpPosition pos = makeJumpPos();
		jm.addPosition(pos);
		jm.addPosition(pos);

		assertThat(jm.getPrev(), nullValue());
		assertThat(jm.getNext(), nullValue());
	}

	private JumpPosition makeJumpPos() {
		return new JumpPosition(new TextNode(""), 0, 0);
	}

	/*
	* test finite state machine
	*
	* */
	@Test
	public void testNavigation3() throws IllegalAccessException {
		//first click, not jump
		JumpPosition pos1 = makeJumpPos();
		if(jm.size() == 0){
		}
		assertThat((Integer) currentPos.get(jm), is(0));


		//second click different class/ function/ filed
		JumpPosition pos2 = makeJumpPos();
		jm.addPosition(pos1);	//add current position
		jm.addPosition(pos2);	//add jump position

		assertThat((Integer) currentPos.get(jm), is(1));
	}

	@Test
	public void testNavigation4() throws IllegalAccessException {
		//first click, not jump
		JumpPosition pos0 = makeJumpPos();
		jm.addPosition(pos0);
		assertThat((Integer) currentPos.get(jm), is(0));
		assertThat(jm.getPrev(),is(nullValue()));
		assertThat(jm.getNext(),is(nullValue()));
		jm.addPosition(pos0);
		assertThat((Integer) currentPos.get(jm), is(0));

		//second click different class/ function/ filed, go to the new pos
		JumpPosition pos1 = makeJumpPos();
		jm.addPosition(pos1);
		assertThat((Integer) currentPos.get(jm), is(1));
		assertThat(jm.getNext(), is(nullValue()));
		jm.addPosition(pos1);
		assertThat((Integer) currentPos.get(jm), is(1));
		jm.getPrev();
		assertThat((Integer) currentPos.get(jm),is(0));
		jm.getNext();
		assertThat((Integer) currentPos.get(jm), is(1));
//		jm.addPosition(pos0);
//		assertThat((Integer) currentPos.get(jm),is(0));

		//the last
		JumpPosition pos2 = makeJumpPos();
		jm.addPosition(pos2);
		assertThat((Integer) currentPos.get(jm), is(2));
		assertThat(jm.getNext(), is(nullValue()));
		jm.addPosition(pos2);
		assertThat((Integer) currentPos.get(jm), is(2));
		jm.getPrev();
		assertThat((Integer) currentPos.get(jm),is(1));
		jm.getNext();
		assertThat((Integer) currentPos.get(jm), is(2));

		//test reset state
		jm.reset();
		jm.addPosition(pos0); //in gui, there will be a start position as current, so add it here to simulate the process
		assertThat((Integer) currentPos.get(jm), is(0));
		assertThat(jm.getPrev(),is(nullValue()));
		assertThat(jm.getNext(),is(nullValue()));
		jm.addPosition(pos0);
		assertThat((Integer) currentPos.get(jm), is(0));
	}


}
