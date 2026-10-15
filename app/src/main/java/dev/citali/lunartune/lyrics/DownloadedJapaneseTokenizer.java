/*
 * LunarTune (2026)
 * Copyright (C) cognitiveshadows03 - github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 and Section 5
 *
 * Resource-resolver adapter pattern based on ArchiveTune's
 * DownloadedJapaneseTokenizer (GPL-3.0), with Kuromoji's IPADIC API (Apache-2.0).
 */

package dev.citali.lunartune.lyrics;

import com.atilika.kuromoji.TokenizerBase;
import com.atilika.kuromoji.dict.Dictionary;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.util.ResourceResolver;
import com.atilika.kuromoji.viterbi.TokenFactory;
import com.atilika.kuromoji.viterbi.ViterbiNode;

import java.util.List;

/** IPADIC tokenizer whose binary dictionary resources are supplied by a downloaded archive. */
public final class DownloadedJapaneseTokenizer extends TokenizerBase {
    private DownloadedJapaneseTokenizer(Builder builder) {
        configure(builder);
    }

    @Override
    public List<Token> tokenize(String text) {
        return createTokenList(text);
    }

    public static DownloadedJapaneseTokenizer create(ResourceResolver resolver) {
        return new DownloadedJapaneseTokenizer(new Builder(resolver));
    }

    private static final class Builder extends TokenizerBase.Builder {
        Builder(ResourceResolver resolver) {
            this.resolver = resolver;
            totalFeatures = 9;
            readingFeature = 7;
            partOfSpeechFeature = 0;
            tokenFactory = new TokenFactory<Token>() {
                @Override
                public Token createToken(
                    int wordId,
                    String surface,
                    ViterbiNode.Type type,
                    int position,
                    Dictionary dictionary
                ) {
                    return new Token(wordId, surface, type, position, dictionary);
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TokenizerBase> T build() {
            return (T) new DownloadedJapaneseTokenizer(this);
        }
    }
}
