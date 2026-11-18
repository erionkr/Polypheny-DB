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

package org.polypheny.db.algebra.core;

import java.util.List;
import java.util.Optional;
import org.polypheny.db.algebra.AlgCollations;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.fun.AggFunction;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.PolyType;

public class LaxAggregateCall {

    public final String name;
    public final AggFunction function;
    private final RexNode input;
    /**
     * Whether the aggregate sees each distinct value once. Cypher writes this inside the call,
     * as collect(DISTINCT x), and dropping it changes the answer rather than its shape.
     */
    public final boolean distinct;


    public LaxAggregateCall( String name, AggFunction function, RexNode input ) {
        this( name, function, input, false );
    }


    public LaxAggregateCall( String name, AggFunction function, RexNode input, boolean distinct ) {
        this.name = name;
        this.function = function;
        this.input = input;
        this.distinct = distinct;
    }


    public static LaxAggregateCall create( String name, AggFunction function, RexNode input ) {
        return new LaxAggregateCall( name, function, input );
    }


    public static LaxAggregateCall from( AggregateCall call, AlgNode parent ) {
        if ( parent == null ) {
            return new LaxAggregateCall( call.name, call.getAggregation(), null, call.isDistinct() );
        }

        RexNode node;
        if ( call.getArgList().isEmpty() ) {
            node = new RexIndexRef( 0, parent.getTupleType().getFields().get( 0 ).getType() );
        } else {
            node = new RexIndexRef( call.getArgList().get( 0 ), parent.getTupleType().getFields().get( call.getArgList().get( 0 ) ).getType() );
        }

        return new LaxAggregateCall( call.name, call.getAggregation(), node, call.isDistinct() );
    }


    public Optional<RexNode> getInput() {
        return Optional.ofNullable( input );
    }


    public AggregateCall toAggCall( AlgDataType rowType, AlgCluster cluster ) {
        int index = rowType.getFieldNames().indexOf( name );
        if ( index < 0 && getInput().isPresent() && getInput().get().unwrap( RexIndexRef.class ).isPresent() ) {
            index = getInput().get().unwrap( RexIndexRef.class ).get().getIndex();
        }

        return AggregateCall.create( function, false, false, index < 0 ? List.of() : List.of( index ), -1, AlgCollations.EMPTY, getType( cluster ), name );
    }


    public AlgDataType getType( AlgCluster cluster ) {
        AlgDataType type = getType( cluster, function, getInput().map( RexNode::getType ).orElse( null ) );
        if ( type == null ) {
            throw new GenericRuntimeException( "Unknown aggregate function: " + function.getKind() );
        }
        return type;
    }


    public static AlgDataType getType( AlgCluster cluster, AggFunction function ) {
        return getType( cluster, function, null );
    }


    public static AlgDataType getType( AlgCluster cluster, AggFunction function, AlgDataType inputType ) {
        return switch ( function.getKind() ) {
            case COUNT -> cluster.getTypeFactory().createPolyType( PolyType.BIGINT );
            case AVG -> cluster.getTypeFactory().createTypeWithNullability( cluster.getTypeFactory().createPolyType( PolyType.DOUBLE ), true );
            // A sum, minimum or maximum returns the type it was given. Deriving a double here
            // is what turned an integer sum of 153 into 153.0 on the way back from the store.
            case SUM, MIN, MAX -> inputType == null
                    ? cluster.getTypeFactory().createTypeWithNullability( cluster.getTypeFactory().createPolyType( PolyType.DOUBLE ), true )
                    : cluster.getTypeFactory().createTypeWithNullability( inputType, true );
            // collect gathers its input into a list rather than reducing it to one value.
            case COLLECT -> cluster.getTypeFactory().createArrayType(
                    cluster.getTypeFactory().createPolyType( PolyType.ANY ), -1 );
            default -> null;
        };
    }


    public Optional<AlgDataType> requiresCast( AlgCluster cluster ) {
        return switch ( function.getKind() ) {
            case COUNT -> Optional.empty();
            case AVG -> Optional.of( cluster.getTypeFactory().createTypeWithNullability( cluster.getTypeFactory().createPolyType( PolyType.DOUBLE ), true ) );
            // A sum, minimum or maximum keeps the type of its input, so casting that input to
            // a double contradicts the type derived for the call itself and the planner rejects
            // the plan. An input with no concrete type, as in the document model, still needs it.
            case SUM, MAX, MIN -> {
                AlgDataType in = getInput().map( RexNode::getType ).orElse( null );
                yield in == null || in.getPolyType() == PolyType.ANY || in.getPolyType() == PolyType.DOCUMENT
                        ? Optional.of( cluster.getTypeFactory().createTypeWithNullability( cluster.getTypeFactory().createPolyType( PolyType.DOUBLE ), true ) )
                        : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

}
