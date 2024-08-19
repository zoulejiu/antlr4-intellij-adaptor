package org.antlr.intellij.adaptor.lexer;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Utils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * The factory that automatically maps all tokens and rule names into
 * IElementType objects: {@link TokenIElementType} and {@link RuleIElementType}.
 * <p>
 * This caches all mappings for each Language that use this factory. I.e.,
 * it's not keeping an instance per plugin/Language.
 */
public class PSIElementTypeFactory {
    private static final Map<Language, List<TokenIElementType>> tokenIElementTypesCache = new HashMap<>();
    private static final Map<Language, List<RuleIElementType>> ruleIElementTypesCache = new HashMap<>();
    private static final Map<Language, Map<String, Integer>> tokenNamesCache = new HashMap<>();
    private static final Map<Language, Map<String, Integer>> ruleNamesCache = new HashMap<>();
    private static final Map<Language, TokenIElementType> eofIElementTypesCache = new HashMap<>();
    private static final Map<Language, Map<String, TokenIElementType>> tokenCache = new HashMap<>();

    private PSIElementTypeFactory() {
    }

    public static void defineLanguageIElementTypes(Language language, Vocabulary vocabulary, String[] ruleNames) {
        synchronized (PSIElementTypeFactory.class) {
            tokenIElementTypesCache.computeIfAbsent(language, l -> createTokenIElementTypes(l, vocabulary));
            ruleIElementTypesCache.computeIfAbsent(language, l -> createRuleIElementTypes(l, ruleNames, false));
            tokenNamesCache.computeIfAbsent(language, l -> createTokenTypeMap(vocabulary));
            ruleNamesCache.computeIfAbsent(language, l -> createRuleIndexMap(ruleNames));
        }
    }

    public static void defineLanguageIElementTypes(Language language, Vocabulary vocabulary, String[] ruleNames, boolean register) {
        synchronized (PSIElementTypeFactory.class) {
            tokenIElementTypesCache.computeIfAbsent(language, l -> createTokenIElementTypes(l, vocabulary));
            ruleIElementTypesCache.computeIfAbsent(language, l -> createRuleIElementTypes(l, ruleNames, register));
            tokenNamesCache.computeIfAbsent(language, l -> createTokenTypeMap(vocabulary));
            ruleNamesCache.computeIfAbsent(language, l -> createRuleIndexMap(ruleNames));
        }
    }

    public static TokenIElementType getEofElementType(Language language) {
        return eofIElementTypesCache.computeIfAbsent(language,
                l -> new TokenIElementType(Token.EOF, "EOF", l));
    }

    public static List<TokenIElementType> getTokenIElementTypes(Language language) {
        return tokenIElementTypesCache.get(language);
    }

    public static List<RuleIElementType> getRuleIElementTypes(Language language) {
        return ruleIElementTypesCache.get(language);
    }

    public static Map<String, Integer> getRuleNameToIndexMap(Language language) {
        return ruleNamesCache.get(language);
    }

    public static Map<String, Integer> getTokenNameToTypeMap(Language language) {
        return tokenNamesCache.get(language);
    }

    /**
     * Get a map from token names to token types.
     */
    public static Map<String, Integer> createTokenTypeMap(Vocabulary vocabulary) {
        return IntStream.rangeClosed(0, vocabulary.getMaxTokenType()).boxed()
                .collect(toMap(vocabulary::getDisplayName, identity()));
    }

    /**
     * Get a map from rule names to rule indexes.
     */
    public static Map<String, Integer> createRuleIndexMap(String[] ruleNames) {
        return Utils.toMap(ruleNames);
    }

    @NotNull
    public static List<TokenIElementType> createTokenIElementTypes(Language language, Vocabulary vocabulary) {
        return IntStream.rangeClosed(0, vocabulary.getMaxTokenType()).boxed()
                .map(i -> getInstance(i, vocabulary.getDisplayName(i), language, true))
                .collect(toList());
    }

    public static TokenIElementType getInstance(int antlrTokenType,
                                                @NotNull @NonNls String debugName,
                                                @Nullable Language language, boolean register) {
        Language baseLanguage = language == null ? Language.ANY.getBaseLanguage() : language.getBaseLanguage();
        if (baseLanguage != null) {
            if (tokenCache.containsKey(baseLanguage)) {
                Map<String, TokenIElementType> tokenIElementTypeMap = tokenCache.get(baseLanguage);
                if (tokenIElementTypeMap.containsKey(debugName)) {
                    TokenIElementType tokenIElementType = tokenIElementTypeMap.get(debugName);
                    tokenIElementType.setAntlrTokenType(antlrTokenType);
                    return tokenIElementType;
                }
            }
        } else {
            baseLanguage = Language.ANY;
        }
        Language registerLanguage =null;
        if(language!=null){
            if(language.getBaseLanguage()!=null){
                registerLanguage=language.getBaseLanguage();
            }else {
                registerLanguage=language;
            }
        }
        TokenIElementType tokenIElementType = new TokenIElementType(antlrTokenType, debugName, registerLanguage, register);
        if (tokenCache.containsKey(baseLanguage)) {
            Map<String, TokenIElementType> tokenIElementTypeMap = tokenCache.get(baseLanguage);
            tokenIElementTypeMap.put(debugName, tokenIElementType);
        } else {
            Map<String, TokenIElementType> tokenIElementTypeMap = new HashMap<>();
            tokenIElementTypeMap.put(debugName, tokenIElementType);
            tokenCache.put(baseLanguage, tokenIElementTypeMap);
        }
        return tokenIElementType;
    }

    @NotNull
    public static List<RuleIElementType> createRuleIElementTypes(Language language, String[] ruleNames, boolean register) {
        List<RuleIElementType> result;
        RuleIElementType[] elementTypes = new RuleIElementType[ruleNames.length];
        for (int i = 0; i < ruleNames.length; i++) {
            elementTypes[i] = new RuleIElementType(i, ruleNames[i], language, register);
        }

        result = List.of(elementTypes);
        return result;
    }

    public static TokenSet createTokenSet(Language language, int... types) {
        List<TokenIElementType> tokenIElementTypes = getTokenIElementTypes(language);

        IElementType[] elementTypes = new IElementType[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Token.EOF) {
                elementTypes[i] = getEofElementType(language);
            } else {
                if (tokenIElementTypes != null && tokenIElementTypes.size() - 1 >= types[i]) {
                    elementTypes[i] = tokenIElementTypes.get(types[i]);
                }
            }
        }

        return TokenSet.create(elementTypes);
    }
}
