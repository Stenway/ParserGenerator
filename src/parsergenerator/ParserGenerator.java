package parsergenerator;

import com.stenway.grammarsml.Charset;
import com.stenway.grammarsml.CharsetItem;
import com.stenway.grammarsml.CiAny;
import com.stenway.grammarsml.CiCategory;
import com.stenway.grammarsml.CiChar;
import com.stenway.grammarsml.CiCharsetReference;
import com.stenway.grammarsml.CiPreset;
import com.stenway.grammarsml.CiRange;
import com.stenway.grammarsml.RiAlternative;
import com.stenway.grammarsml.RiGroup;
import com.stenway.grammarsml.RiNot;
import com.stenway.grammarsml.RiOccurrence;
import com.stenway.grammarsml.RiRequired;
import com.stenway.grammarsml.RiRuleReference;
import com.stenway.grammarsml.RiTokenReference;
import com.stenway.grammarsml.Rule;
import com.stenway.grammarsml.RuleItem;
import com.stenway.grammarsml.SmlGrammar;
import com.stenway.grammarsml.TiAlternative;
import com.stenway.grammarsml.TiCharsetReference;
import com.stenway.grammarsml.TiGroup;
import com.stenway.grammarsml.TiOccurrence;
import com.stenway.grammarsml.TiString;
import com.stenway.grammarsml.Token;
import com.stenway.grammarsml.TokenCategories;
import com.stenway.grammarsml.TokenCategory;
import com.stenway.grammarsml.TokenItem;
import com.stenway.grammarsml.UnicodeCategory;
import com.stenway.grammarsml.Utils;
import com.stenway.reliabletxt.ReliableTxtDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class ParserGenerator {
	SmlGrammar grammar;
	JavaDocument document = new JavaDocument();
	
	public ParserGenerator(SmlGrammar grammar) {
		this.grammar = grammar;
	}
	
	private String getHexStr(int value) {
		String hexString = Integer.toHexString(value).toUpperCase();
		if (hexString.length() % 2 == 1) {
			hexString = "0"+hexString;
		}
		return "0x"+hexString;
	}
	
	private String getCategoryCode(UnicodeCategory value) {
		switch(value) {
			case OTHER_CONTROL:				return "CONTROL";
			case OTHER_FORMAT:				return "FORMAT";
			case OTHER_PRIVATEUSE:			return "PRIVATE_USE";
			case OTHER_SURROGATE:			return "SURROGATE";
			case OTHER_NOTASSIGNED:			return "UNASSIGNED";
			case LETTER_LOWERCASE:			return "LOWERCASE_LETTER";
			case LETTER_MODIFIER:			return "MODIFIER_LETTER";
			case LETTER_OTHER:				return "OTHER_LETTER";
			case LETTER_TITLECASE:			return "TITLECASE_LETTER";
			case LETTER_UPPERCASE:			return "UPPERCASE_LETTER";
			case MARK_SPACING:				return "COMBINING_SPACING_MARK";
			case MARK_ENCLOSING:			return "ENCLOSING_MARK";
			case MARK_NONSPACING:			return "NON_SPACING_MARK";
			case NUMBER_DECIMAL:			return "DECIMAL_DIGIT_NUMBER";
			case NUMBER_LETTER:				return "LETTER_NUMBER";
			case NUMBER_OTHER:				return "OTHER_NUMBER";
			case PUNCTUATION_CONNECTOR:		return "CONNECTOR_PUNCTUATION";
			case PUNCTUATION_DASH:			return "DASH_PUNCTUATION";
			case PUNCTUATION_CLOSE:			return "END_PUNCTUATION";
			case PUNCTUATION_FINALQUOTE:	return "FINAL_QUOTE_PUNCTUATION";
			case PUNCTUATION_INITIALQUOTE:	return "INITIAL_QUOTE_PUNCTUATION";
			case PUNCTUATION_OTHER:			return "OTHER_PUNCTUATION";
			case PUNCTUATION_OPEN:			return "START_PUNCTUATION";
			case SYMBOL_CURRENCY:			return "CURRENCY_SYMBOL";
			case SYMBOL_MODIFIER:			return "MODIFIER_SYMBOL";
			case SYMBOL_MATH:				return "MATH_SYMBOL";
			case SYMBOL_OTHER:				return "OTHER_SYMBOL";
			case SEPARATOR_LINE:			return "LINE_SEPARATOR";
			case SEPARATOR_PARAGRAPH:		return "PARAGRAPH_SEPARATOR";
			case SEPARATOR_SPACE:			return "SPACE_SEPARATOR";
		}
		throw new IllegalStateException();
	}
	
	private String getCharsetItemsCode(ArrayList<CharsetItem> items) {
		String result = "(";
		boolean isFirst = true;
		for (CharsetItem item : items) {
			if (isFirst) {
				isFirst = false;
			} else {
				result += " || ";
			}
			result += "(";
			if (item instanceof CiChar) {
				CiChar itemChar = (CiChar)item;
				result += "c == " + getHexStr(itemChar.Value);
			} else if (item instanceof CiAny) {
				result += "(c >= 0 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0x10FFFF)";
			} else if (item instanceof CiCharsetReference) {
				CiCharsetReference charsetReference = (CiCharsetReference)item;
				result += getCharsetCode(charsetReference.Charset);
			} else if (item instanceof CiRange) {
				CiRange range = (CiRange)item;
				result += "c >= "+getHexStr(range.From.Value)+" && c <= "+getHexStr(range.To.Value)+"";
			} else if (item instanceof CiCategory) {
				CiCategory itemCategory = (CiCategory)item;
				result += "Character.getType(c) == Character." + getCategoryCode(itemCategory.Value);
			} else if (item instanceof CiPreset) {
				CiPreset preset = (CiPreset)item;
				if (preset.Value == CiPreset.Letter) {
					result += "Character.isLetter(c)";
				} else if (preset.Value == CiPreset.Digit) {
					result += "Character.isDigit(c)";
				} else {
					throw new IllegalStateException("Todo");
				}
			} else {
				throw new IllegalStateException("Todo");
			}
			result += ")";
		}
		return result + ")";
	}
	
	private String getCharsetCode(Charset charset) {
		String result = getCharsetItemsCode(charset.Included);
		if (charset.Excluded.size() > 0) {
			result += " && !(";
			result += getCharsetItemsCode(charset.Excluded);
			result += ")";
		}
		return result;
	}
	
	private void generateTiGroupCode(TiGroup group) {
		for (TokenItem item : group.Items) {
			if (item instanceof TiCharsetReference) {
				TiCharsetReference charsetReference = (TiCharsetReference)item;
				document.open("if (!isChar_"+charsetReference.Charset.Id+"(offset)) ");
				document.appendLine("return false;");
				document.close();
				document.appendLine("offset++;");
			} else if (item instanceof TiAlternative) {
				TiAlternative alternative = (TiAlternative)item;
				String str = "";
				boolean isFirst = true;				
				for (TokenItem alternativeItem : alternative.Items) {
					TiGroup alternativeGroup = (TiGroup)alternativeItem;
					if (isFirst) {
						isFirst = false;
					} else {
						str += " || ";
					}
					str += lookupTiGroup.get(alternativeGroup) + "(offset)";
				}
				document.open("if (!("+str+")) ");
				document.appendLine("return false;");
				document.close();
				document.appendLine("offset += lastSubLength;");
			} else if (item instanceof TiOccurrence) {
				TiOccurrence occurance = (TiOccurrence)item;
				TiGroup occurranceGroup = (TiGroup)occurance.Item;
				String str = lookupTiGroup.get(occurranceGroup) + "(offset)";
				if (occurance.isOptional()) {
					document.open("if ("+str+") ");
					document.appendLine("offset += lastSubLength;");
					document.close();
				} else if (occurance.isRepeatAsterisk()) {
					document.open("while (true) ");
					document.open("if (!("+str+")) ");
					document.appendLine("break;");
					document.close();
					document.appendLine("offset += lastSubLength;");
					document.close();
				} else if (occurance.isRepeatPlus()) {
					document.open("if (!("+str+")) ");
					document.appendLine("return false;");
					document.close();
					document.appendLine("offset += lastSubLength;");
					
					document.open("while (true) ");
					document.open("if (!("+str+")) ");
					document.appendLine("break;");
					document.close();
					document.appendLine("offset += lastSubLength;");
					document.close();
				}
			} else if (item instanceof TiGroup) {
				TiGroup itemGroup = (TiGroup)item;
				String str = lookupTiGroup.get(itemGroup) + "(offset)";
				document.open("if (!("+str+")) ");
				document.appendLine("return false;");
				document.close();
				document.appendLine("offset += lastSubLength;");
			} else if (item instanceof TiString) {
				TiString string = (TiString)item;
				int[] codePoints = string.Value.codePoints().toArray();
				String codePointStr = "";
				boolean isFirst = true;
				for (int codePoint : codePoints) {
					if (isFirst) {
						isFirst = false;
					} else {
						codePointStr += ", ";
					}
					codePointStr += getHexStr(codePoint);
				}
				String stringMethod = "isString";
				if (string.IgnoreCase) {
					stringMethod = "isCiString";
				}
				document.open("if (!("+stringMethod+"(offset, new int[] {"+codePointStr+"}))) ");
				document.appendLine("return false;");
				document.close();
				document.appendLine("offset += "+codePoints.length+";");
			} else {
				throw new IllegalStateException("Todo");
			}
			document.appendLine();
		}
	}
	
	HashMap<TiGroup, String> lookupTiGroup = new HashMap<>();
	
	private void generateTokenSubCode(TiGroup group) {
		document.appendLine();
		String name = lookupTiGroup.get(group);
		document.open("protected boolean "+name+"(int offset) ");
		document.appendLine("int startOffset = offset;");
		
		generateTiGroupCode(group);
		
		document.appendLine("lastSubLength = offset-startOffset;");
		document.appendLine("return true;");
		document.close();
	}
	
	private void prepareTokenItems(ArrayList<TokenItem> items) {
		for (TokenItem item : items) {
			if (item instanceof TiGroup) {
				TiGroup group = (TiGroup)item;
				prepareTokenItems(group.Items);
			} else if (item instanceof TiAlternative) {
				TiAlternative alternative = (TiAlternative)item;
				for (int i=0; i<alternative.Items.size(); i++) {
					TokenItem alternativeItem = alternative.Items.get(i);
					if (!(alternativeItem instanceof TiGroup)) {
						TiGroup newGroup = new TiGroup();
						newGroup.Items.add(alternativeItem);
						alternative.Items.set(i, newGroup);
						prepareTokenItems(newGroup.Items);
					} else {
						TiGroup group = (TiGroup)alternativeItem;
						prepareTokenItems(group.Items);
					}
				}
			} else if (item instanceof TiOccurrence) {
				TiOccurrence occurance = (TiOccurrence)item;
				if (!(occurance.Item instanceof TiGroup)) {
					TiGroup newGroup = new TiGroup();
					newGroup.Items.add(occurance.Item);
					occurance.Item = newGroup;
					prepareTokenItems(newGroup.Items);
				} else {
					TiGroup group = (TiGroup)occurance.Item;
					prepareTokenItems(group.Items);
				}
			}
		}
	}
	
	private void generateTokenCode(Token token) {
		lookupTiGroup.clear();
		prepareTokenItems(token.Group.Items);
		TiGroup[] groups = Utils.getTiSubGroups(token);
		int counter = 1;
		for (TiGroup group : groups) {
			String name = "isTokenSub"+counter+"_"+token.Id;
			lookupTiGroup.put(group, name);
			counter++;
		}
		for (TiGroup group : groups) {
			generateTokenSubCode(group);
		}
		document.appendLine();
		document.open("public boolean isToken_"+token.Id+"() ");
		document.appendLine("int offset = 0;");
		
		generateTiGroupCode(token.Group);
		
		document.appendLine("lastTokenLength = offset;");
		document.appendLine("return true;");
		document.close();
	}
	
	private void generateCharIterator() {
		document.open("class CharIterator ");
		document.appendLine("protected final int[] chars;");
		document.appendLine("protected int index;");
		document.appendLine("protected int lastSubLength;");
		document.appendLine("protected int lastTokenLength;");
		document.appendLine();
		
		document.open("public CharIterator(String text) ");
		document.appendLine("chars = text.codePoints().toArray();");
		document.close();
		document.appendLine();
		
		document.open("public boolean isEndOfText() ");
		document.appendLine("return isEndOfText(0);");
		document.close();
		document.appendLine();
		
		document.open("protected boolean isEndOfText(int offset) ");
		document.appendLine("return index + offset >= chars.length;");
		document.close();
		document.appendLine();
		
		document.open("public int getIndex() ");
		document.appendLine("return index;");
		document.close();
		document.appendLine();
		
		document.open("public int[] getChars() ");
		document.appendLine("return chars;");
		document.close();
		document.appendLine();
		
		document.appendLine("@Override");
		document.open("public String toString() ");
		document.appendLine("if (isEndOfText()) { return \"END OF TEXT\"; }");
		document.appendLine("int endIndex = Math.min(index+20, chars.length);");
		document.appendLine("return new String(chars, index, endIndex-index);");
		document.close();
		document.appendLine();
		
		document.open("public int[] getLineInfo() ");
		document.appendLine("int lineIndex = 0;");
		document.appendLine("int linePosition = 0;");
		document.open("for (int i=0; i<index; i++) ");
		document.open("if (chars[i] == '\\n') ");
		document.appendLine("lineIndex++;");
		document.appendLine("linePosition = 0;");
		document.closeOpen("} else {");
		document.appendLine("linePosition++;");
		document.close();
		document.close();
		document.appendLine("return new int[] {lineIndex, linePosition};");
		document.close();
		document.appendLine();
		
		document.open("public void resetIndex(int index) ");
		document.appendLine("this.index = index;");
		document.close();
		document.appendLine();
		
		document.open("protected boolean isString(int offset, int[] strChars) ");
		document.open("if (index + offset + strChars.length > chars.length) ");
		document.appendLine("return false;");
		document.close();
		document.open("for (int i=0; i<strChars.length; i++) ");
		document.open("if (chars[index+offset+i] != strChars[i]) ");
		document.appendLine("return false;");
		document.close();
		document.close();
		document.appendLine("return true;");
		document.close();
		document.appendLine();
		
		document.open("protected boolean isCiString(int offset, int[] strChars) ");
		document.open("if (index + offset + strChars.length > chars.length) ");
		document.appendLine("return false;");
		document.close();
		document.open("for (int i=0; i<strChars.length; i++) ");
		document.open("if (Character.toLowerCase(chars[index+offset+i]) != Character.toLowerCase(strChars[i])) ");
		document.appendLine("return false;");
		document.close();
		document.close();
		document.appendLine("return true;");
		document.close();
		document.appendLine();
		
		for (Charset charset : grammar.Charsets.all()) {
			document.appendLine();
			document.open("protected boolean isChar_"+charset.Id+"(int offset) ");
			document.appendLine("if (isEndOfText(offset)) return false;");
			document.appendLine("int c = chars[index+offset];");
			document.appendLine("return "+getCharsetCode(charset)+";");
			document.close();
		}
		
		for (Token token : grammar.Tokens.all()) {
			generateTokenCode(token);
		}
		
		document.appendLine();
		document.open("public int readToken() ");
		document.appendLine("index += lastTokenLength;");
		document.appendLine("return lastTokenLength;");
		document.close();
		
		document.close();
	}
	
	private void generateTokensEnum() {
		document.open("enum TokenType ");
		Token[] tokens = grammar.Tokens.all();
		for (int i=0; i<tokens.length; i++) {
			Token token = tokens[i];
			String comma = "";
			if (i < tokens.length-1) {
				comma = ",";
			}
			document.appendLine(token.Id.toUpperCase()+comma);
		}
		document.close();
	}
	
	private void generateRulesEnum() {
		document.open("enum RuleType ");
		Rule[] rules = grammar.Rules.all();
		for (int i=0; i<rules.length; i++) {
			Rule rule = rules[i];
			String comma = "";
			if (i < rules.length-1) {
				comma = ",";
			}
			document.appendLine(rule.Id.toUpperCase()+comma);
		}
		document.close();
	}
	
	private void generateTokenCategoriesEnum() {
		document.open("enum TokenCategory ");
		TokenCategory[] categories = grammar.TokenCategories.all();
		for (int i=0; i<categories.length; i++) {
			TokenCategory category = categories[i];
			String comma = "";
			if (i < categories.length-1) {
				comma = ",";
			}
			document.appendLine(category.Id.toUpperCase()+comma);
		}
		document.close();
	}
	
	HashMap<RiGroup, String> lookupRiGroup = new HashMap<>();
	
	private void generateRuleSubCode(RiGroup group, Rule rule) {
		document.appendLine();
		String name = lookupRiGroup.get(group);
		document.open("protected boolean "+name+"(RuleNode parentRuleNode, boolean optional) ");
		document.appendLine("RuleNode ruleNode = new RuleNode(RuleType."+rule.Id.toUpperCase()+");");
		
		generateRiGroupCode(group, rule);
		
		document.appendLine("parentRuleNode.addTemp(ruleNode);");
		document.appendLine("return true;");
		document.close();
	}
		
	private void prepareRuleItems(ArrayList<RuleItem> items) {
		for (RuleItem item : items) {
			if (item instanceof RiGroup) {
				RiGroup group = (RiGroup)item;
				prepareRuleItems(group.Items);
			} else if (item instanceof RiAlternative) {
				RiAlternative alternative = (RiAlternative)item;
				for (int i=0; i<alternative.Items.size(); i++) {
					RuleItem alternativeItem = alternative.Items.get(i);
					if (!(alternativeItem instanceof RiGroup)) {
						RiGroup newGroup = new RiGroup();
						newGroup.Items.add(alternativeItem);
						alternative.Items.set(i, newGroup);
						prepareRuleItems(newGroup.Items);
					} else {
						RiGroup group = (RiGroup)alternativeItem;
						prepareRuleItems(group.Items);
					}
				}
			} else if (item instanceof RiOccurrence) {
				RiOccurrence occurance = (RiOccurrence)item;
				if (!(occurance.Item instanceof RiGroup)) {
					RiGroup newGroup = new RiGroup();
					newGroup.Items.add(occurance.Item);
					occurance.Item = newGroup;
					prepareRuleItems(newGroup.Items);
				} else {
					RiGroup group = (RiGroup)occurance.Item;
					prepareRuleItems(group.Items);
				}
			} else if (item instanceof RiNot) {
				RiNot not = (RiNot)item;
				if (!(not.Item instanceof RiGroup)) {
					RiGroup newGroup = new RiGroup();
					newGroup.Items.add(not.Item);
					not.Item = newGroup;
					prepareRuleItems(newGroup.Items);
				} else {
					RiGroup group = (RiGroup)not.Item;
					prepareRuleItems(group.Items);
				}
			}
		}
	}

	private void generateRuleCode(Rule rule) {
		lookupRiGroup.clear();
		prepareRuleItems(rule.Group.Items);
		RiGroup[] groups = Utils.getRiSubGroups(rule);
		int counter = 1;
		for (RiGroup group : groups) {
			String name = "parseRuleSub"+counter+"_"+rule.Id;
			lookupRiGroup.put(group, name);
			counter++;
		}
		for (RiGroup group : groups) {
			generateRuleSubCode(group, rule);
		}
		document.appendLine();
		document.open("public boolean parseRule_"+rule.Id+"(boolean optional) ");
		document.appendLine("RuleNode ruleNode = new RuleNode(RuleType."+rule.Id.toUpperCase()+");");
		if (rule == grammar.RootRule) {
			document.appendLine("RootNode = ruleNode;");
		}
		generateRiGroupCode(rule.Group, rule);
		
		document.appendLine("lastNode = ruleNode;");
		document.appendLine("return true;");
		document.close();
		document.appendLine();
	}
	
	private void generateRiGroupCode(RiGroup group, Rule rule) {
		document.appendLine("int startIndex = iterator.getIndex();");
		for (RuleItem item : group.Items) {
			if (item instanceof RiTokenReference) {
				RiTokenReference tokenReference = (RiTokenReference)item;
				document.open("if (!iterator.isToken_"+tokenReference.Token.Id+"()) ");
				document.appendLine("return returnFalseOrThrowException(optional, startIndex, \"Token of type '"+tokenReference.Token.Id.toUpperCase()+"' expected in rule '"+rule.Id.toUpperCase()+"'\");");
				document.close();
				document.appendLine("ruleNode.addToken(TokenType."+tokenReference.Token.Id.toUpperCase()+", iterator.readToken());");
			} else if (item instanceof RiRuleReference) {
				RiRuleReference ruleReference = (RiRuleReference)item;
				document.open("if (!parseRule_"+ruleReference.Rule.Id+"(optional || false)) ");
				document.appendLine("iterator.resetIndex(startIndex);");
				document.appendLine("return false;");
				document.close();
				document.appendLine("ruleNode.add(lastNode);");
			} else if (item instanceof RiAlternative) {
				RiAlternative alternative = (RiAlternative)item;
				String str = "";
				boolean isFirst = true;				
				for (RuleItem alternativeItem : alternative.Items) {
					RiGroup alternativeGroup = (RiGroup)alternativeItem;
					if (isFirst) {
						isFirst = false;
					} else {
						str += " || ";
					}
					str += lookupRiGroup.get(alternativeGroup) + "(ruleNode, true)";
				}
				document.open("if (!("+str+")) ");
				document.appendLine("return returnFalseOrThrowException(optional, startIndex, \"Non optional rule alternative could not be matched\");");
				document.close();
			} else if (item instanceof RiOccurrence) {
				RiOccurrence occurance = (RiOccurrence)item;
				RiGroup occurranceGroup = (RiGroup)occurance.Item;
				String str = lookupRiGroup.get(occurranceGroup) + "(ruleNode";
				if (occurance.isOptional()) {
					document.appendLine(str+", true);");
				} else if (occurance.isRepeatAsterisk()) {
					document.open("while (true) ");
					document.open("if (!("+str+", true))) ");
					document.appendLine("break;");
					document.close();
					document.close();
				} else if (occurance.isRepeatPlus()) {
					document.open("if (!("+str+", optional || false)) ");
					document.appendLine("return returnFalseOrThrowException(optional, startIndex, \"Non optional rule repeat could not be matched\");");
					document.close();
					
					document.open("while (true) ");
					document.open("if (!("+str+", true))) ");
					document.appendLine("break;");
					document.close();
					document.close();
				}
			} else if (item instanceof RiGroup) {
				RiGroup itemGroup = (RiGroup)item;
				String str = lookupRiGroup.get(itemGroup) + "(ruleNode, optional || false)";
				
				document.open("if (!("+str+") ");
				document.appendLine("return returnFalseOrThrowException(optional, startIndex, \"Non optional rule group could not be matched\");");
				document.close();
			} else if (item instanceof RiRequired) {
				document.appendLine("optional = false;");
			} else if (item instanceof RiNot) {
				RiNot not = (RiNot)item;
				RiGroup notGroup = (RiGroup)not.Item;
				String str = lookupRiGroup.get(notGroup) + "(ruleNode";
				document.open("if ("+str+", true)) ");
				document.appendLine("return false;");
				document.close();
			} else {
				throw new IllegalStateException();
			}
			document.appendLine();
		}
	}
	
	private void generateParserClass() {
		document.open("public class Parser ");
			
		document.appendLine("CharIterator iterator;");
		document.appendLine("ParserTreeNode lastNode;");
		document.appendLine("public RuleNode RootNode;");
		document.appendLine();
		
		document.open("public Parser(String content) ");
		document.appendLine("iterator = new CharIterator(content);");
		document.close();
		document.appendLine();
		
		document.open("protected ParserException getException(String message) ");
		document.appendLine("int[] lineInfo = iterator.getLineInfo();");
		document.appendLine("return new ParserException(message, lineInfo);");
		document.close();
		document.appendLine();
		
		document.open("protected boolean returnFalseOrThrowException(boolean optional, int startIndex, String message) ");
		document.open("if (!optional) ");
		document.appendLine("throw getException(message);");
		document.close();
		document.appendLine("iterator.resetIndex(startIndex);");
		document.appendLine("return false;");
		document.close();
		document.appendLine();
		
		for (Rule rule : grammar.Rules.all()) {
			generateRuleCode(rule);
		}
		
		document.open("public RuleNode parse() ");
		document.appendLine("boolean result = parseRule_"+grammar.RootRule.Id+"(false);");
		document.open("if (!result) ");
		document.appendLine("throw getException(\"Could not match root rule\");");
		document.close();
		document.open("if (!iterator.isEndOfText()) ");
		document.appendLine("throw getException(\"Text not completely parsed\");");
		document.close();
		document.appendLine("return (RuleNode)lastNode;");
		document.close();
		document.appendLine();
		
		document.open("public static RuleNode parse(String content) ");
		document.appendLine("return new Parser(content).parse();");
		document.close();
		
		document.close();
	}
	
	private void generateRuleNodeClass() {
		document.open("class RuleNode extends ParserTreeNode ");
		document.appendLine("public final RuleType Type;");
		document.appendLine("public final ArrayList<ParserTreeNode> Children = new ArrayList<>();");
		document.appendLine();
		
		document.open("public RuleNode(RuleType type) ");
		document.appendLine("Type = type;");
		document.close();
		document.appendLine();
		
		document.open("public void addToken(TokenType type, int length) ");
		document.appendLine("Children.add(new Token(type, length));");
		document.close();
		document.appendLine();
		
		document.open("public void add(ParserTreeNode node) ");
		document.appendLine("Children.add(node);");
		document.close();
		document.appendLine();
		
		document.open("public void addTemp(RuleNode node) ");
		document.open("for (ParserTreeNode child : node.Children) ");
		document.appendLine("Children.add(child);");
		document.close();
		document.close();
		document.appendLine();
		
		document.appendLine("@Override");
		document.open("public String toString() ");
		document.appendLine("return \"Rule \"+Type.name();");
		document.close();
		
		
		document.close();
		document.appendLine();
	}
	
	private void generateTokenClass() {
		document.open("class Token extends ParserTreeNode ");
		document.appendLine("public final TokenType Type;");
		document.appendLine("public final int Length;");
		document.appendLine();
		
		document.open("public Token(TokenType type, int length) ");
		document.appendLine("Type = type;");
		document.appendLine("Length = length;");
		document.close();
		document.appendLine();
		
		document.appendLine("@Override");
		document.open("public String toString() ");
		document.appendLine("return \"Token \"+Type.name() + \" (\" + Length + \")\";");
		document.close();
		
		document.close();
		document.appendLine();
	}
	
	private void generateParserExceptionClass() {
		document.open("class ParserException extends RuntimeException ");
		
		document.open("public ParserException(String message) ");
		document.appendLine("super(message);");
		document.close();
		
		document.open("public ParserException(String message, int[] lineInfo) ");
		document.appendLine("super(message + \" (Line \" + (lineInfo[0]+1) + \", Char \" + (lineInfo[1]+1) + \")\");");
		document.close();
		
		document.close();
		document.appendLine();
	}
	
	private void generateTokenUtilsClass() {
		document.open("class TokenUtils ");
		
		document.open("public static TokenCategory getTokenCategory(Token token) ");
		
		boolean isFirst = true;
		for (TokenCategory category : grammar.TokenCategories.all()) {
			for (Token token : category.Tokens) {
				String ifStr = "if (token.Type == TokenType."+token.Id.toUpperCase()+") ";
				if (isFirst) {
					document.open(ifStr);
					isFirst = false;
				} else {
					document.closeOpen("} else "+ifStr+"{");
				}
				String returnStr = "";

				document.appendLine("return TokenCategory."+category.Id.toUpperCase()+";");
			}
		}
		document.close();
		
		document.appendLine("return null;");
		document.close();
		
		document.close();
		document.appendLine();
	}
	
	private void generateUtilClasses() {
		generateParserExceptionClass();
		
		document.open("class ParserTreeNode ");
		document.close();
		document.appendLine();
		
		generateRuleNodeClass();
		
		generateTokenClass();
		
		generateTokenUtilsClass();
		
		document.appendLine();
	}
	
	public void generate(String packageName, String filePath) throws IOException {
		document.appendLine("package "+packageName+";");
		document.appendLine();
		document.appendLine("import java.util.ArrayList;");
		document.appendLine();
		
		generateCharIterator();
		document.appendLine();
		
		generateTokensEnum();
		document.appendLine();
		
		generateRulesEnum();
		document.appendLine();
		
		generateTokenCategoriesEnum();
		document.appendLine();
		
		generateUtilClasses();
		generateParserClass();
				
		String strContent = document.toString();
		//ReliableTxtDocument.save(strContent, filePath);
		Files.write( Paths.get(filePath), strContent.getBytes());
	}
}
