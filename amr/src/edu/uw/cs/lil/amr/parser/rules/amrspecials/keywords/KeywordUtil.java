package edu.uw.cs.lil.amr.parser.rules.amrspecials.keywords;

import edu.cornell.cs.nlp.spf.ccg.categories.Category;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicLanguageServices;
import edu.cornell.cs.nlp.spf.mr.lambda.LogicalExpression;

public class KeywordUtil {

	public static boolean isValidCategory(Category<LogicalExpression> category) {
		// Verify the typing.
		return category.getSemantics() != null
				&& category.getSemantics().getType().isComplex()
				&& LogicLanguageServices.getTypeRepository()
						.getTruthValueType()
						.equals(category.getSemantics().getType().getRange());
	}

}
