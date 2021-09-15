package parsergenerator;

import com.stenway.grammarsml.SmlGrammar;

public class Program {

	public static void main(String[] args) {
		try {
			String grammarFilePath = "D:\\Grammar\\Wsv.grammar";
			String outputFilePath = "d:\\Grammar\\Generated.java";
			
			SmlGrammar grammar = SmlGrammar.load(grammarFilePath);
			ParserGenerator parserGenerator = new ParserGenerator(grammar);
			parserGenerator.generate(outputFilePath);
			System.out.println("SUCCESS");
			
		} catch (Exception e) {
			System.out.println("[ERROR] "+e.getClass().getName() + ": " + e.getMessage());
		}
	}
}