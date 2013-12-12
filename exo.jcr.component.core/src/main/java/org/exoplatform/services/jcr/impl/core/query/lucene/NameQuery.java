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
package org.exoplatform.services.jcr.impl.core.query.lucene;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.exoplatform.services.jcr.datamodel.IllegalNameException;
import org.exoplatform.services.jcr.datamodel.InternalQName;

import java.io.IOException;
import java.util.Set;

/**
 * <code>NameQuery</code> implements a query for the name of a node.
 */
public class NameQuery extends Query {

   /**
    * The serial version UID
    */
   private static final long serialVersionUID = 6411494650704435343L;

   /**
     * The node name.
     */
    private final InternalQName nodeName;

    /**
     * The index format version.
     */
    private final IndexFormatVersion version;

    /**
     * The internal namespace mappings of the index.
     */
    private final NamespaceMappings nsMappings;

    /**
     * Creates a new <code>NameQuery</code>.
     *
     * @param nodeName   the name of the nodes to return.
     * @param version    the version of the index.
     * @param nsMappings the namespace mappings of the index.
     */
    public NameQuery(InternalQName nodeName,
                     IndexFormatVersion version,
                     NamespaceMappings nsMappings) {
        this.nodeName = nodeName;
        this.version = version;
        this.nsMappings = nsMappings;
    }

    /**
     * @return the name of the nodes to return.
     */
    public InternalQName getName() {
        return nodeName;
    }

    /**
     * {@inheritDoc}
     */
    public Query rewrite(IndexReader reader) throws IOException {
        if (version.getVersion() >= IndexFormatVersion.V3.getVersion()) {
            // use LOCAL_NAME and NAMESPACE_URI field
            BooleanQuery name = new BooleanQuery();
            name.add(new JcrTermQuery(new Term(FieldNames.NAMESPACE_URI, nodeName.getNamespace())),
                    BooleanClause.Occur.MUST);
            name.add(new JcrTermQuery(new Term(FieldNames.LOCAL_NAME,
                    nodeName.getName())),
                    BooleanClause.Occur.MUST);
            return name;
        } else {
            // use LABEL field
            try {
                return new JcrTermQuery(new Term(FieldNames.LABEL,
                        nsMappings.translateName(nodeName)));
            } catch (IllegalNameException e) {
                throw Util.createIOException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void extractTerms(Set<Term> terms) {
    }

    /**
     * {@inheritDoc}
     */
    public String toString(String field) {
        return "name() = " + nodeName.toString();
    }
}
