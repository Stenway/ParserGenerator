package parsergenerator;

import com.stenway.grammarsml.SmlGrammar;

public class Program {

	public static void main(String[] args) {
		try {
			String grammarFilePath = "D:\\LimaScript\\LimaScript.grammar";
			//String outputFilePath = "D:\\NetBeans\\ParserGeneratorTest\\src\\test\\Parser.java";
			
			SmlGrammar grammar = SmlGrammar.load(grammarFilePath);
			ParserGenerator parserGenerator = new ParserGenerator(grammar);
			
			boolean buildTest = false;
			if (buildTest) {
				parserGenerator.generate("test", "D:\\NetBeans\\ParserGeneratorTest\\src\\test\\Parser.java");
			} else {
				parserGenerator.generate("docgen", "D:\\NetBeans\\DocGen\\DocGen\\src\\docgen\\Parser.java");
			}
			System.out.println("SUCCESS");
			
		} catch (Exception e) {
			System.out.println("[ERROR] "+e.getClass().getName() + ": " + e.getMessage());
		}
	}
}