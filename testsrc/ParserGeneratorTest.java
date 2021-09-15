package test;

import com.stenway.reliabletxt.ReliableTxtDocument;
import com.stenway.sml.SmlDocument;
import com.stenway.sml.SmlElement;
import java.io.IOException;


public class ParserGeneratorTest {

	private static int convert(RuleNode parentRuleNode, SmlElement parentElement, int[] chars, int index) {
		for (ParserTreeNode node : parentRuleNode.Children) {
			if (node instanceof Token) {
				Token token = (Token)node;
				String value = new String(chars, index, token.Length);
				index += token.Length;
				parentElement.addAttribute(token.Type.name(), value);
			} else {
				RuleNode ruleNode = (RuleNode)node;
				SmlElement element = parentElement.addElement(ruleNode.Type.name());
				index = convert(ruleNode, element, chars, index);
			}
		}
		return index;
	}
	
	private static void output(RuleNode rootNode, String text) throws IOException {
		int[] chars = text.codePoints().toArray();
		SmlDocument document = new SmlDocument(rootNode.Type.name());
		convert(rootNode, document.getRoot(), chars, 0);
		document.save("d:\\Grammar\\Output.sml");
	}
	
	public static void main(String[] args) {
		try {
			String text = ReliableTxtDocument.load("d:\\Grammar\\Input.txt").getText();
			RuleNode rootNode = Parser.parse(text);
			output(rootNode, text);
			
			System.out.println("SUCCESS");
		} catch (Exception e) {
			System.out.println("[ERROR] "+e.getMessage());
		}
	}
	
}
