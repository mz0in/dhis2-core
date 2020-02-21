package org.hisp.dhis.program.function;

/*
 * Copyright (c) 2004-2020, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.hisp.dhis.antlr.ParserExceptionWithoutContext;
import org.hisp.dhis.parser.expression.CommonExpressionVisitor;
import org.hisp.dhis.program.ProgramExpressionItem;
import org.hisp.dhis.relationship.RelationshipType;

import static org.hisp.dhis.antlr.AntlrParserUtils.trimQuotes;
import static org.hisp.dhis.parser.expression.CommonExpressionVisitor.DEFAULT_DOUBLE_VALUE;
import static org.hisp.dhis.parser.expression.antlr.ExpressionParser.ExprContext;

/**
 * Program indicator function: d2 relationship count
 *
 * @author Jim Grace
 */
public class D2RelationshipCount
    extends ProgramExpressionItem
{
    @Override
    public Object getDescription( ExprContext ctx, CommonExpressionVisitor visitor )
    {
        if ( ctx.QUOTED_UID() != null )
        {
            String relationshipId = trimQuotes( ctx.QUOTED_UID().getText() );

            RelationshipType relationshipType = visitor.getRelationshipTypeService().getRelationshipType( relationshipId );

            if ( relationshipType == null )
            {
                throw new ParserExceptionWithoutContext( "Relationship type " + relationshipId + " not found" );
            }

            visitor.getItemDescriptions().put( ctx.QUOTED_UID().getText(), relationshipType.getDisplayName() );
        }

        return DEFAULT_DOUBLE_VALUE;
    }

    @Override
    public Object getSql( ExprContext ctx, CommonExpressionVisitor visitor )
    {
        String relationshipIdConstraint = "";

        if ( ctx.QUOTED_UID() != null )
        {
            String relationshipId = trimQuotes( ctx.QUOTED_UID().getText() );

            relationshipIdConstraint =
                " join relationshiptype rt on r.relationshiptypeid = rt.relationshiptypeid and rt.uid = '"
                    + relationshipId + "'";
        }

        return "(select count(*) from relationship r" + relationshipIdConstraint +
            " join relationshipitem rifrom on rifrom.relationshipid = r.relationshipid" +
            " join trackedentityinstance tei on rifrom.trackedentityinstanceid = tei.trackedentityinstanceid and tei.uid = ax.tei)";
    }
}
