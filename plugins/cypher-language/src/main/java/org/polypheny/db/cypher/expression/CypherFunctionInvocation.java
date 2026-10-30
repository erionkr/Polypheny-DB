/*
 * Copyright 2019-2024 The Polypheny Project
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

package org.polypheny.db.cypher.expression;

import static org.polypheny.db.algebra.operators.OperatorName.CYPHER_POINT;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.cypher.cypher2alg.CypherToAlgConverter.CypherContext;
import org.polypheny.db.cypher.cypher2alg.CypherToAlgConverter.RexType;
import org.polypheny.db.languages.OperatorRegistry;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.util.Pair;

@Getter
public class CypherFunctionInvocation extends CypherExpression {

    private final ParserPos namePos;
    private final List<String> namespace;
    private final boolean distinct;
    private final List<CypherExpression> arguments;

    private static final List<String> operatorNames = Arrays.stream( OperatorName.values() ).map( Enum::name ).toList();
    private final OperatorName op;


    public CypherFunctionInvocation( ParserPos beforePos, ParserPos namePos, List<String> namespace, String image, boolean distinct, List<CypherExpression> arguments ) {
        super( beforePos );
        this.namePos = namePos;
        this.namespace = namespace;
        if ( operatorNames.contains( image.toUpperCase( Locale.ROOT ) ) ) {
            this.op = OperatorName.valueOf( image.toUpperCase( Locale.ROOT ) );
        } else if ( operatorNames.contains( "CYPHER_" + image.toUpperCase( Locale.ROOT ) ) ) {
            this.op = OperatorName.valueOf( "CYPHER_" + image.toUpperCase( Locale.ROOT ) );
        } else if ( image.equals( "withinBBox" ) ) {
            this.op = OperatorName.CYPHER_WITHIN_BBOX;
        } else if ( image.equals( "withinGeometry" ) ) {
            this.op = OperatorName.CYPHER_WITHIN_GEOMETRY;
        } else {
            throw new GenericRuntimeException( "Used function is not supported!" );
        }
        this.distinct = distinct;
        this.arguments = arguments;
    }


    private CypherFunctionInvocation( CypherFunctionInvocation original, List<CypherExpression> arguments ) {
        super( original.pos );
        this.namePos = original.namePos;
        this.namespace = original.namespace;
        this.op = original.op;
        this.distinct = original.distinct;
        this.arguments = arguments;
    }


    /**
     * The same call with different arguments.
     */
    public CypherFunctionInvocation withArguments( List<CypherExpression> arguments ) {
        return new CypherFunctionInvocation( this, arguments );
    }


    public ImmutableList<CypherExpression> getArguments() {
        return ImmutableList.copyOf( arguments );
    }


    public OperatorName getOperatorName() {
        return op;
    }


    /**
     * A name for this call, built from the function and its argument.
     * <p>
     * ORDER BY identifies what to sort by through names, and a function call has none of its
     * own. This gives it one that is stable for the same call written twice, which is what the
     * hidden column added for it is keyed on.
     */
    @Override
    public String getName() {
        // The name ends up as a column name, which Neo4j reads as an identifier, so it cannot
        // carry the brackets and dots the call is written with.
        return ("__" + getOperatorName().name() + "_" + getArguments().get( 0 ).getName())
                .replaceAll( "[^A-Za-z0-9_]", "_" );
    }


    @Override
    public Pair<PolyString, RexNode> getRex( CypherContext context, RexType type ) {
        // At this point, we do not know what is on the left side of the Pair.
        // The caller has to discard the left side, and use a variable name or something else.
        return Pair.of( PolyString.of( "???" ), getRexCall( context ) );
    }


    public RexNode getRexCall( CypherContext context ) {
        switch ( getOperatorName() ) {
            case CYPHER_POINT: {
                // VERY UGLY, but it works for now. This could be improved by using the function MAP_OF_ENTRIES,
                // but I am not sure how to call it.
                CypherLiteral mapExpression = (CypherLiteral) getArguments().get( 0 );
                List<RexNode> arguments = new ArrayList<>();
                mapExpression.getMapValue().forEach( ( key, value ) -> {
                    Pair<PolyString, RexNode> pair = value.getRex( context, RexType.PROJECT );
                    arguments.add( context.rexBuilder.makeLiteral( key ) );
                    arguments.add( pair.right );
                } );
                // Fill with NULL to make sure we have the correct amount of arguments.
                // 3 coordinates + 3 names + srid + crs = up to 8 possible
                while ( arguments.size() < 10 ) {
                    arguments.add( context.rexBuilder.makeNullLiteral( context.typeFactory.createUnknownType() ) );
                }
                return new RexCall(
                        context.geometryType,
                        OperatorRegistry.get( QueryLanguage.from( "cypher" ), CYPHER_POINT ),
                        arguments );
            }
            case DISTANCE: {
                return new RexCall(
                        context.numberType,
                        // If the third argument for the point.distance function is the string 'neo4j', then
                        // use the spherical distance approximation of Neo4j. This uses a different earth radius.
                        // Otherwise, the default behavior is to use the same distance measurement as the other
                        // models.
                        OperatorRegistry.get( QueryLanguage.from( "cypher" ), arguments.size() == 3
                                && arguments.get( 2 ) instanceof CypherLiteral neo4jFlag
                                && neo4jFlag.getValue().toString().equals( "neo4j" )
                                ? OperatorName.DISTANCE_NEO4J
                                : OperatorName.DISTANCE
                        ),
                        List.of(
                                arguments.get( 0 ).getRex( context, RexType.PROJECT ).getRight(),
                                arguments.get( 1 ).getRex( context, RexType.PROJECT ).getRight()
                        ) );
            }
            case DISTANCE_NEO4J: {
                return new RexCall(
                        context.numberType,
                        OperatorRegistry.get( QueryLanguage.from( "cypher" ), OperatorName.DISTANCE_NEO4J ),
                        List.of(
                                arguments.get( 0 ).getRex( context, RexType.PROJECT ).getRight(),
                                arguments.get( 1 ).getRex( context, RexType.PROJECT ).getRight()
                        ) );
            }
            case CYPHER_WITHIN_BBOX:
                return new RexCall(
                        context.booleanType,
                        OperatorRegistry.get( QueryLanguage.from( "cypher" ), OperatorName.CYPHER_WITHIN_BBOX ),
                        List.of(
                                arguments.get( 0 ).getRex( context, RexType.PROJECT ).getRight(),
                                // CypherFunctionInvocation.getRex -> throw
                                // Because create function logic is implemented in
                                arguments.get( 1 ).getRex( context, RexType.PROJECT ).getRight(),
                                arguments.get( 2 ).getRex( context, RexType.PROJECT ).getRight()
                        ) );
            case CYPHER_WITHIN_GEOMETRY:
                return new RexCall(
                        context.booleanType,
                        OperatorRegistry.get( QueryLanguage.from( "cypher" ), OperatorName.CYPHER_WITHIN_GEOMETRY ),
                        List.of(
                                arguments.get( 0 ).getRex( context, RexType.PROJECT ).getRight(),
                                // CypherFunctionInvocation.getRex -> throw
                                // Because create function logic is implemented in
                                arguments.get( 1 ).getRex( context, RexType.PROJECT ).getRight()
                        ) );
            case COALESCE: {
                // The Neo4j adapter already writes this one back out as Cypher, so the generic
                // operator carries it through unchanged. The arguments all hold the same kind of
                // value, so the first one's type stands for the result; leaving it unknown loses
                // the value on the way back.
                List<RexNode> coalesced = getArguments().stream()
                        .map( a -> a.getRex( context, RexType.PROJECT ).getRight() )
                        .toList();
                return new RexCall(
                        coalesced.get( 0 ).getType(),
                        OperatorRegistry.get( OperatorName.COALESCE ),
                        coalesced );
            }
            // These take one argument and hand it to the store unchanged. The adapter knows how
            // to write each one back out, so the only thing needed here is the return type.
            case CYPHER_TOINTEGER:
                return oneArgument( context, PolyType.BIGINT );
            case CYPHER_TOFLOAT:
                return oneArgument( context, PolyType.DOUBLE );
            case CYPHER_FLOOR:
                return oneArgument( context, PolyType.DOUBLE );
            case CYPHER_SIZE:
                return oneArgument( context, PolyType.BIGINT );
            default:
                throw new NotImplementedException( "Cypher Function to alg conversion missing: " + getOperatorName() );
        }
    }


    /**
     * A call to this function with its single argument, typed as given.
     */
    private RexNode oneArgument( CypherContext context, PolyType returnType ) {
        return new RexCall(
                context.typeFactory.createPolyType( returnType ),
                OperatorRegistry.get( QueryLanguage.from( "cypher" ), getOperatorName() ),
                getArguments().get( 0 ).getRex( context, RexType.PROJECT ).getRight() );
    }

}
