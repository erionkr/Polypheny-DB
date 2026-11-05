/*
 * Copyright 2019-2023 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.cypher.clause;

import java.util.List;
import lombok.Getter;
import java.util.ArrayList;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.cypher.cypher2alg.CypherToAlgConverter.CypherContext;
import org.polypheny.db.cypher.cypher2alg.CypherToAlgConverter.RexType;
import org.polypheny.db.languages.OperatorRegistry;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.util.Pair;
import org.polypheny.db.cypher.expression.CypherExpression;
import org.polypheny.db.languages.ParserPos;


@Getter
public class CypherCase extends CypherExpression {

    private final CypherExpression condition;
    private final List<CypherExpression> when;
    private final List<CypherExpression> then;
    private final CypherExpression elseCase;


    public CypherCase(
            ParserPos pos,
            CypherExpression condition,
            List<CypherExpression> when,
            List<CypherExpression> then,
            CypherExpression elseCase ) {
        super( pos );
        this.condition = condition;
        this.when = when;
        this.then = then;
        this.elseCase = elseCase;
    }


    /**
     * The case as a single expression: the conditions and their results in turn, with the else
     * branch last. That is the shape the CASE operator expects, and the Neo4j adapter writes it
     * back out as CASE WHEN ... THEN ... ELSE ... END.
     */
    @Override
    public Pair<PolyString, RexNode> getRex( CypherContext context, RexType type ) {
        List<RexNode> operands = new ArrayList<>();
        for ( int i = 0; i < when.size(); i++ ) {
            operands.add( when.get( i ).getRex( context, type ).right );
            operands.add( then.get( i ).getRex( context, type ).right );
        }
        if ( elseCase != null ) {
            operands.add( elseCase.getRex( context, type ).right );
        }

        return Pair.of( null, new RexCall(
                operands.get( 1 ).getType(),
                OperatorRegistry.get( OperatorName.CASE ),
                operands ) );
    }


    @Override
    public CypherKind getCypherKind() {
        return CypherKind.CASE;
    }

}
