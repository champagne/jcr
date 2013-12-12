/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exoplatform.services.jcr.impl.core.query.lucene.spell;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.exoplatform.commons.utils.SecurityHelper;
import org.exoplatform.services.jcr.impl.core.query.QueryHandler;
import org.exoplatform.services.jcr.impl.core.query.QueryRootNode;
import org.exoplatform.services.jcr.impl.core.query.RelationQueryNode;
import org.exoplatform.services.jcr.impl.core.query.TraversingQueryNodeVisitor;
import org.exoplatform.services.jcr.impl.core.query.lucene.FieldNames;
import org.exoplatform.services.jcr.impl.core.query.lucene.SearchIndex;
import org.exoplatform.services.jcr.impl.core.query.lucene.Util;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;

/**
 * <code>LuceneSpellChecker</code> implements a spell checker based on the terms
 * present in a lucene index.
 */
public class LuceneSpellChecker implements org.exoplatform.services.jcr.impl.core.query.lucene.SpellChecker
{

   /**
    * Logger instance for this class.
    */
   private static final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.LuceneSpellChecker");

   public static final class FiveSecondsRefreshInterval extends LuceneSpellChecker
   {
      public FiveSecondsRefreshInterval()
      {
         super(5 * 1000);
      }
   }

   public static final class OneMinuteRefreshInterval extends LuceneSpellChecker
   {
      public OneMinuteRefreshInterval()
      {
         super(60 * 1000);
      }
   }

   public static final class FiveMinutesRefreshInterval extends LuceneSpellChecker
   {
      public FiveMinutesRefreshInterval()
      {
         super(5 * 60 * 1000);
      }
   }

   public static final class ThirtyMinutesRefreshInterval extends LuceneSpellChecker
   {
      public ThirtyMinutesRefreshInterval()
      {
         super(30 * 60 * 1000);
      }
   }

   public static final class OneHourRefreshInterval extends LuceneSpellChecker
   {
      public OneHourRefreshInterval()
      {
         super(60 * 60 * 1000);
      }
   }

   public static final class SixHoursRefreshInterval extends LuceneSpellChecker
   {
      public SixHoursRefreshInterval()
      {
         super(6 * 60 * 60 * 1000);
      }
   }

   public static final class TwelveHoursRefreshInterval extends LuceneSpellChecker
   {
      public TwelveHoursRefreshInterval()
      {
         super(12 * 60 * 60 * 1000);
      }
   }

   public static final class OneDayRefreshInterval extends LuceneSpellChecker
   {
      public OneDayRefreshInterval()
      {
         super(24 * 60 * 60 * 1000);
      }
   }

   /**
    * The internal spell checker.
    */
   private InternalSpellChecker spellChecker;

   /**
    * The refresh interval.
    */
   private final long refreshInterval;

   /**
    * Spell checker with a default refresh interval of one hour.
    */
   public LuceneSpellChecker()
   {
      this(60 * 60 * 1000); // default refresh interval: one hour
   }

   protected LuceneSpellChecker(long refreshInterval)
   {
      this.refreshInterval = refreshInterval;
   }

   /**
    * {@inheritDoc}
    */
   public void init(QueryHandler handler, float minDistance, boolean morePopular) throws IOException
   {
      if (handler instanceof SearchIndex)
      {
         this.spellChecker = new InternalSpellChecker((SearchIndex)handler, minDistance, morePopular);
      }
      else
      {
         throw new IOException("LuceneSpellChecker only works with " + SearchIndex.class.getName());
      }
   }

   /**
    * {@inheritDoc}
    * 
    * @throws RepositoryException
    */
   public String check(QueryRootNode aqt) throws IOException, RepositoryException
   {
      String stmt = getFulltextStatement(aqt);
      if (stmt == null)
      {
         // no spellcheck operation in query
         return null;
      }
      return spellChecker.suggest(stmt);
   }

   public void close()
   {
      spellChecker.close();
   }

   // ------------------------------< internal >--------------------------------

   /**
    * Returns the fulltext statement of a spellcheck relation query node or
    * <code>null</code> if none exists in the abstract query tree.
    * 
    * @param aqt
    *            the abstract query tree.
    * @return the fulltext statement or <code>null</code>.
    * @throws RepositoryException
    */
   private String getFulltextStatement(QueryRootNode aqt) throws RepositoryException
   {
      final String[] stmt = new String[1];
      aqt.accept(new TraversingQueryNodeVisitor()
      {
         @Override
         public Object visit(RelationQueryNode node, Object o) throws RepositoryException
         {
            if (stmt[0] == null && node.getOperation() == RelationQueryNode.OPERATION_SPELLCHECK)
            {
               stmt[0] = node.getStringValue();
            }
            return super.visit(node, o);
         }
      }, null);
      return stmt[0];
   }

   private final class InternalSpellChecker
   {

      /**
       * Timestamp when the last refresh was done.
       */
      private long lastRefresh;

      /**
       * Set to true while a refresh is done in a separate thread.
       */
      private boolean refreshing = false;

      /**
       * The query handler associated with this spell checker.
       */
      private final SearchIndex handler;

      /**
       * The directory where the spell index is stored.
       */
      private Directory spellIndexDirectory;

      /**
       * The underlying spell checker.
       */
      private SpellChecker spellChecker;

      private final boolean morePopular;

      /**
       * Creates a new internal spell checker.
       * 
       * @param handler
       *            the associated query handler.
       * @param minDistance
       *            minimal distance between  word and proposed close word. Float value 0..1.
       * @param morePopular
       *            return only the suggest words that are as frequent or more frequent than the searched word 
       */
      InternalSpellChecker(final SearchIndex handler, float minDistance, boolean morePopular) throws IOException
      {
         this.handler = handler;
         spellIndexDirectory = null;
         SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<Object>()
         {
            public Object run() throws Exception
            {
               spellIndexDirectory = handler.getDirectoryManager().getDirectory("spellchecker");

               if (IndexReader.indexExists(spellIndexDirectory))
               {
                  lastRefresh = System.currentTimeMillis();
               }
               return null;
            }
         });
         this.spellChecker = new SpellChecker(spellIndexDirectory);
         this.spellChecker.setAccuracy(minDistance);
         this.morePopular = morePopular;
         refreshSpellChecker();
      }

      /**
       * Checks a fulltext query statement and suggests a spell checked
       * version of the statement. If the spell checker thinks the spelling is
       * correct <code>null</code> is returned.
       * 
       * @param statement
       *            the fulltext query statement.
       * @return a suggestion or <code>null</code>.
       */
      String suggest(String statement) throws IOException
      {
         // tokenize the statement (field name doesn't matter actually...)
         List<String> words = new ArrayList<String>();
         List<TokenData> tokens = new ArrayList<TokenData>();
         tokenize(statement, words, tokens);

         String[] suggestions = check(words.toArray(new String[words.size()]));
         if (suggestions != null)
         {
            // replace words in statement in reverse order because length
            // of statement will change
            StringBuilder sb = new StringBuilder(statement);
            for (int i = suggestions.length - 1; i >= 0; i--)
            {
               TokenData t = tokens.get(i);
               // only replace if word acutally changed
               if (!t.word.equalsIgnoreCase(suggestions[i]))
               {
                  sb.replace(t.startOffset, t.endOffset, suggestions[i]);
               }
            }
            // if suggestion is same as a statement return null
            String result = sb.toString();
            if (statement.equalsIgnoreCase(result))
            {
               return null;
            }
            else
            {
               return result;
            }
         }
         else
         {
            return null;
         }
      }

      void close()
      {
         try
         {
            SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<Object>()
            {
               public Object run() throws Exception
               {
                  spellIndexDirectory.close();
                  return null;
               }
            });
         }
         catch (IOException e)
         {
            if (LOG.isTraceEnabled())
            {
               LOG.trace("An exception occurred: " + e.getMessage());
            }
         }
         // urgh, the lucene spell checker cannot be closed explicitly.
         // finalize will close the reader...
         spellChecker = null;
      }

      /**
       * Tokenizes the statement into words and tokens.
       * 
       * @param statement
       *            the fulltext query statement.
       * @param words
       *            this list will be filled with the original words extracted
       *            from the statement.
       * @param tokens
       *            this list will be filled with the tokens parsed from the
       *            statement.
       * @throws IOException
       *             if an error occurs while parsing the statement.
       */
      private void tokenize(String statement, List<String> words, List<TokenData> tokens) throws IOException
      {
         TokenStream ts = handler.getTextAnalyzer().tokenStream(FieldNames.FULLTEXT, new StringReader(statement));
         CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);
         PositionIncrementAttribute positionIncrement = ts.getAttribute(PositionIncrementAttribute.class);
         OffsetAttribute offset = ts.getAttribute(OffsetAttribute.class);
         try
         {
            String word;
            while (ts.incrementToken())
            {

               word = new String(term.buffer(), 0, term.length());
               //            while ((t = ts.next()) != null)
               //            {
               String origWord = statement.substring(offset.startOffset(), offset.endOffset());
               if (positionIncrement.getPositionIncrement() > 0)
               {
                  words.add(word);
                  tokens.add(new TokenData(offset.startOffset(), offset.endOffset(), word));
               }
               else
               {
                  // very simple implementation: use termText with length
                  // closer to original word
                  TokenData current = tokens.get(tokens.size() - 1);
                  if (Math.abs(origWord.length() - current.termLength()) > Math.abs(origWord.length() - word.length()))
                  {
                     // replace current token and word
                     words.set(words.size() - 1, word);
                     tokens.set(tokens.size() - 1, new TokenData(offset.startOffset(), offset.endOffset(), word));
                  }
               }
            }
         }
         finally
         {
            ts.end();
            ts.close();
         }
      }

      class TokenData
      {
         int startOffset;

         int endOffset;

         String word;

         public TokenData(int startOffset, int endOffset, String word)
         {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.word = word;
         }

         /**
          * @return
          */
         public int termLength()
         {
            return word.length();
         }

      }

      /**
       * Checks the spelling of the passed <code>words</code> and returns a
       * suggestion.
       * 
       * @param words
       *            the words to check.
       * @return a suggestion of correctly spelled <code>words</code> or
       *         <code>null</code> if this spell checker thinks
       *         <code>words</code> are spelled correctly.
       * @throws IOException
       *             if an error occurs while spell checking.
       */
      private String[] check(final String words[]) throws IOException
      {
         refreshSpellChecker();
         boolean hasSuggestion = false;
         final IndexReader reader = handler.getIndexReader();
         try
         {
            for (int retries = 0; retries < 100; retries++)
            {
               try
               {
                  String[] suggestion = new String[words.length];
                  for (int i = 0; i < words.length; i++)
                  {
                     final int currentIndex = i;
                     String[] similar =
                        SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<String[]>()
                        {
                           public String[] run() throws Exception
                           {
                              return spellChecker
                                 .suggestSimilar(words[currentIndex], 5, reader, FieldNames.FULLTEXT, morePopular
                                    ? SuggestMode.SUGGEST_MORE_POPULAR : SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
                           }
                        });

                     if (similar.length > 0)
                     {
                        suggestion[i] = similar[0];
                        hasSuggestion = true;
                     }
                     else
                     {
                        suggestion[i] = words[i];
                     }
                  }
                  if (hasSuggestion)
                  {
                     LOG.debug("Successful after " + new Integer(retries) + " retries");
                     return suggestion;
                  }
                  else
                  {
                     return null;
                  }
               }
               catch (AlreadyClosedException e)
               {
                  // it may happen that the index reader inside the
                  // spell checker is closed while searching for
                  // suggestions. this is actually a design flaw in the
                  // lucene spell checker, but for now we simply retry
                  if (LOG.isTraceEnabled())
                  {
                     LOG.trace("An exception occurred: " + e.getMessage());
                  }
               }
            }
            // unsuccessful after retries
            return null;
         }
         finally
         {
            SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<Object>()
            {
               public Object run() throws Exception
               {
                  Util.closeOrRelease(reader);
                  return null;
               }
            });
         }
      }

      /**
       * Refreshes the underlying spell checker in a background thread.
       * Synchronization is done on this <code>LuceneSpellChecker</code>
       * instance. While the refresh takes place {@link #refreshing} is set to
       * <code>true</code>.
       */
      private void refreshSpellChecker()
      {
         if (lastRefresh + refreshInterval < System.currentTimeMillis())
         {
            synchronized (this)
            {
               if (refreshing)
               {
                  return;
               }
               else
               {
                  refreshing = true;
                  Runnable refresh = new Runnable()
                  {
                     public void run()
                     {

                        try
                        {
                           SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<Object>()
                           {
                              public Object run() throws Exception
                              {
                                 IndexReader reader = handler.getIndexReader();
                                 try
                                 {
                                    long time = System.currentTimeMillis();
                                    Dictionary dict = new LuceneDictionary(reader, FieldNames.FULLTEXT);
                                    LOG.debug("Starting spell checker index refresh");
                                    spellChecker.indexDictionary(dict, new IndexWriterConfig(Version.LUCENE_36,
                                       new StandardAnalyzer(Version.LUCENE_36)), true);
                                    time = System.currentTimeMillis() - time;
                                    time = time / 1000;
                                    LOG.info("Spell checker index refreshed in: " + new Long(time) + " s.");
                                 }
                                 finally
                                 {
                                    Util.closeOrRelease(reader);
                                    synchronized (InternalSpellChecker.this)
                                    {
                                       refreshing = false;
                                    }
                                 }
                                 return null;
                              }
                           });
                        }
                        catch (IOException e)
                        {
                           if (LOG.isTraceEnabled())
                           {
                              LOG.trace("An exception occurred: " + e.getMessage());
                           }
                        }
                     }
                  };
                  new Thread(refresh, "SpellChecker Refresh").start();

                  lastRefresh = System.currentTimeMillis();
               }
            }
         }
      }
   }
}
