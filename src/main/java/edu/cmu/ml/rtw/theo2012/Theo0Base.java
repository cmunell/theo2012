package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Pair;

/**
 * Abstract base class for {@link Theo0} implementations that implements all of the convenience and
 * library-style methods in terms of a small set of primitives.<p>
 *
 * See discussion in Theo0.java's class-level comments for more.<p>
 *
 * Note that the implementations here are inherently suitable for Theo1 as well because the only
 * material difference is that they must account for the possibility of Theo0 returning null where
 * Theo1 is barred from doing so.<p>
 */
public abstract class Theo0Base implements Theo0 {
    private final static Logger log = LogFactory.getLogger();

    @Override public abstract boolean isOpen();

    @Override public abstract boolean isReadOnly();

    @Override public abstract void setReadOnly(boolean makeReadOnly);

    @Override public abstract Slot getSlot(String slotName);

    @Override public Entity get(RTWLocation location) {
        try {
            if (location.size() == 0)
                throw new RuntimeException("Illegal zero-length location");
            if (location.size() == 1)
                return get(location.getPrimitiveEntity());

            // For now, we pursue the costly arrangement of building up a composite entity piece by
            // piece by iterating through the RTWLocation.  This is mainly a compatability stop-gap.
            // Once the Wedge is further in place (allowing for being less RTWLocation-centric
            // overall) and we've had a chance to see what the performance charactaristics of our
            // current Theo designs and implementations are like, then we can revisit the need for
            // this sort of thing and the general question that it answers of turning an Entity from
            // one Theo implementaion or KB into an Entity from another Theo impelementation or KB.

            Entity e = get(location.getPrimitiveEntity());
            for (int i = 1; i < location.size(); i++) {
                if (location.isSlot(i)) {
                    e = e.getQuery(location.getAsSlot(i));
                } else {
                    RTWValue v = location.getAsValue(i);

                    // If this parenthetical expression is an entity (which includes the case of it
                    // being an RTWPointerValue), then convert it over
                    if (v instanceof Entity)
                        v = get(((Entity)v).getRTWLocation());

                    if (!e.isQuery())
                        throw new RuntimeException("Element reference following a non-query at position " + i);
                    
                    e = e.toQuery().getBelief(v);
                }
            }
            return e;
        } catch (Exception e) {
            throw new RuntimeException("get(" + location + ")", e);
        }
    }

    /* bkdb: get rid of this unless OM winds up needing it
    @Override public Entity get(Entity entity) {
        return get(entity.getRTWLocation());
    }
    */

    public PrimitiveEntity getPrimitiveEntity(String primNameString) {
    	return get(primNameString);
    }

    @Override public Slot createSlot(String master, Slot generalization, String inverseSlave) {
        try {
            Entity m = get(master);
            if (m != null) deleteEntity(m);
            Slot g = generalization;
            if (g == null) g = getSlot("slot");
            m = createPrimitiveEntity(master, g);

            if (inverseSlave != null) {
                Entity s = get(inverseSlave);
                if (s == null) deleteEntity(s);
                Query igens = g.getQuery("inverse");
                if (igens != null && igens.getNumValues() > 0) {
                    s = createPrimitiveEntity(inverseSlave, igens.need1Entity());
                } else {
                    s = createPrimitiveEntity(inverseSlave, g);
                }

                m.addValue("inverse", s);
                s.addValue("inverse", m);
                m.addValue("masterinverse", true);
                s.addValue("masterinverse", false);
            }

            return m.toSlot();
        } catch (Exception e) {
            throw new RuntimeException("createSlot(\"" + master + "\", " + generalization + ", \""
                    + inverseSlave + ")", e);
        }
    }

    @Override public boolean deleteEntity(Entity entity) {
        try {
            boolean deletedSomething = false;
            if (entity.isQuery()) {
                Query q = entity.toQuery();
                while (q != null && q.getNumValues() > 0) {
                    RTWValue v = q.iter().iterator().next();
                    q.deleteValue(v);
                    deletedSomething = true;
                }
            }

            while (true) {
                Collection<Slot> slots = entity.getSlots();
                if (slots.size() == 0) break;

                // FODO: is this system of going through an iterator object going to be a drag?
                // I guess we could add a getAnySlot or use a Collection subclass, but it seems
                // generally ugly no matter which way it's cut.
                Iterator<Slot> sit = slots.iterator();
                Slot slot = sit.next();

                // Have to save generalizations for last
                if (slot.getName().equals("generalizations") && sit.hasNext())
                    slot = sit.next();
                if (deleteEntity(entity.getQuery(slot)))
                    deletedSomething = true;
            }

            if (entity.isBelief()) {
                Belief b = entity.toBelief();
                b.getBeliefQuery().deleteValue(b.getBeliefValue());
            }
            return deletedSomething;
        } catch (Exception e) {
            throw new RuntimeException("deleteEntity(" + entity + ")", e);
        }
    }
    
    @Override public void writeDot( String fileName, String title, Entity startEntity) {
        throw new RuntimeException("writeDot is not implemented.");
    }

    /**
     * Recursive helper function for valueFromString.
     *
     * This eats one RTWValue object (which might be an Entity) from tokenList, starting with token
     * pos.
     *
     * This returns the resulting RTWValue object and the first position at which further parsing
     * should return.
     */
    protected Pair<RTWValue, Integer> valueFromString_recur(List<Object> tokenList, int startPos) {
        try {
            int pos = startPos;            // Don't mutate our starting point
            Pair<RTWValue, Integer> pair;  // General purpose temporary

            // If this is an RTWStringValue, then we're done here
            if (tokenList.get(pos) instanceof RTWStringValue)
                return new Pair<RTWValue, Integer>((RTWStringValue)tokenList.get(pos), pos+1);

            // If we begin with an open paren, then this is a Query or a Belief.   
            if (tokenList.get(pos).equals("(")) {
                pos++;

                // Both Queries and Beliefs demand that the next thing be an Entity, so get that.
                pair = valueFromString_recur(tokenList, pos);
                if (!(pair.getLeft() instanceof Entity))
                    throw new RuntimeException("Expecting entity after opening paren");
                Entity entity = (Entity)pair.getLeft();
                pos = pair.getRight();

                if (pos >= tokenList.size())
                    throw new RuntimeException("Premature end of Entity after " + entity);

                // If we have an equal sign next, then this is a Belief.  Otherwise, it's a Query.
                if (tokenList.get(pos).equals("=")) {
                    if (!entity.isQuery())
                        throw new RuntimeException("Non-Query found immediately preceding an '=': "
                                + entity);
                    Query q = entity.toQuery();

                    pos++;

                    // Final thing is the value for our Belief.
                    pair = valueFromString_recur(tokenList, pos);
                    pos = pair.getRight();

                    Belief b = q.getBelief(pair.getLeft());

                    // Eat closing paren before we return.
                    if (pos >= tokenList.size() || !tokenList.get(pos).equals(")"))
                        throw new RuntimeException("Missing closing paren on belief " + b
                                + ".  Found " + tokenList.get(pos) + " instead.");
                    pos++;

                    return new Pair<RTWValue, Integer>(b, pos);
                }

                // Otherwise, it's a Query
                else {
                    // Final thing is the name of a slot.  That will be expressed as an Entity name.
                    pair = valueFromString_recur(tokenList, pos);
                    if (!(pair.getLeft() instanceof Entity))
                        throw new RuntimeException("Non-slot " + pair.getLeft()
                                + " found in Query after " + entity);
                    Entity slotEntity = (Entity)pair.getLeft();
                    if (!slotEntity.isSlot())
                        throw new RuntimeException(slotEntity + " is not a slot");
                    Slot slot = slotEntity.toSlot();

                    pos = pair.getRight();

                    Query q = entity.getQuery(slot);

                    // Eat closing paren before we return.
                    if (pos >= tokenList.size() || !tokenList.get(pos).equals(")"))
                        throw new RuntimeException("Missing closing paren on query " + q
                                + ".  Found " + tokenList.get(pos) + " instead.");
                    pos++;

                    return new Pair<RTWValue, Integer>(q, pos);
                }
            }

            // If we begin with an open brace, accumulate an RTWListValue until a closing brace is
            // found.
            if (tokenList.get(pos).equals("{")) {
                pos++;
                ArrayList<RTWValue> list = new ArrayList<RTWValue>();
                boolean gotClosingBrace = false;
                while (true) {
                    if (pos >= tokenList.size()) break;
                    if (tokenList.get(pos).equals("}")) {
                        gotClosingBrace = true;
                        break;
                    }
                    pair = valueFromString_recur(tokenList, pos);
                    list.add(pair.getLeft());
                    pos = pair.getRight();
                }
                if (!gotClosingBrace)
                    throw new RuntimeException("Unterminated list");
                return new Pair<RTWValue, Integer>(RTWArrayListValue.construct(list), pos+1);
            }

            // A little error checking: this can't be a ")", "}", or a "="
            String payload = (String)tokenList.get(pos);
            if (payload.equals(")") || payload.equals("}") | payload.equals("="))
                throw new RuntimeException("Found \"" + payload + "\" where a Value was expected");

            // See if our payload has special formatting indicating a particular primitive
            if (payload.equals("$true"))
                return new Pair<RTWValue, Integer>(RTWBooleanValue.TRUE, pos+1);
            if (payload.equals("$false"))
                return new Pair<RTWValue, Integer>(RTWBooleanValue.FALSE, pos+1);
            if (payload.equals("$novalue"))
                return new Pair<RTWValue, Integer>(RTWThisHasNoValue.NONE, pos+1);

            if (payload.charAt(0) == '%')
                return new Pair<RTWValue, Integer>(
                        new RTWIntegerValue(Integer.valueOf(payload.substring(1))),
                        pos+1);

            if (payload.charAt(0) == '#')
                return new Pair<RTWValue, Integer>(
                        new RTWDoubleValue(Double.valueOf(payload.substring(1))),
                        pos+1);

            // Must be a primitive entity name (sadly, one of the most common cases, all the way
            // here at the end.
            return new Pair<RTWValue, Integer>(getPrimitiveEntity(payload), pos+1);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int i = startPos; i < tokenList.size(); i++) {
                if (first) first = false;
                else sb.append(", ");
                sb.append(tokenList.get(i).toString());
            }
            throw new RuntimeException("valueFromString_recur(" + sb.toString() + ")", e);
        }
    }   

    @Override public RTWValue valueFromString(String str) {
        try {
            // First thing to do is to tokenize the given string.  Our primary delimiters of
            // interest are spaces and parentheses.  At least in our current
            // MATLAB-compatable-oriented version of Theo2012, both are defined to be illegal
            // components of an entity name, so we can assume that this is true, and maloperation is
            // reasonable if this assumption is violated because that would me that we'd been given
            // a malformed string.
            //
            // Tokens representing string primitives (which must be surrounded by double-quotes),
            // however, may contain spaces and parenthesis.  So we must remove them first.  We used
            // backslash-escaped strings, so we'll have to look out for escaped double quotes as
            // well as undoing all of the rest of the escaping along the way.  Double quotes outside
            // of string primitives are also said to be invalid, so that simplifies our tokenization
            // as well.
            //
            // So, first we run through the string and break out any string primitives.  String
            // primitives will be represented by RTWStringValue objects (of course), and regular
            // String objects will represent portions of our input string that have yet to be
            // tokenized, and must therefore name Entities or other kinds of RTWValue types (which
            // are trivially identified by leading type characters, as in the case of $true, %42, or
            // #3.14).
            //
            // While we're at it, we'll pull out "(", ")", "}", "{", and "=" into their own tokens,
            // and treat commas like spaces.  All spaces get ignored.  And then all regular String
            // objects other than those denote some kind of RTWValue.  That way we can go directly
            // to a recursive parse of the structure of the input string based on these
            // single-character tokens, and do the RTWValue translation on the fly.
            List<Object> tokenList = new ArrayList<Object>();
            boolean inStringPrimitive = false;
            boolean prevWasBackslash = false;
            int startPos = 0;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '"') {
                    if (inStringPrimitive) {
                        if (prevWasBackslash) {
                            // This was quoted; ignore it
                            prevWasBackslash = false;
                        } else {
                            // This marks the end of a string primitive.  We need to unescape
                            // everything between startPos and i (and exclude the surrounding double
                            // quotes) and then get that into an RTWStringValue.  I don't know what
                            // the fastest way to do that is.  Maybe a reasonable and easy
                            // compromoise is to unescape it character by character, bulding into a
                            // char[].
                            char[] unescaped = new char[i-startPos-1];
                            int k = 0;
                            boolean unescapeNext = false;
                            for (int j = startPos+1; j < i; j++) {
                                char d = str.charAt(j);
                                if (unescapeNext) {
                                    unescaped[k++] = d;
                                    unescapeNext = false;
                                } else if (d == '\\') {
                                    unescapeNext = true;
                                } else {
                                    unescaped[k++] = d;
                                }
                            }
                            RTWStringValue stringPrimitive =
                                    new RTWStringValue(new String(unescaped, 0, k));

                            // Now we can tack this on to the end of tokenList, reset our state, and
                            // continue.
                            tokenList.add(stringPrimitive);
                            inStringPrimitive = false;
                            startPos = i+1;
                        }
                    } else {
                        // A double-quote character outside of a primitive is only ever supposed to
                        // indicate the beginning of a string primitive.  Drop our input string so
                        // far into tokenList, adjust our state, and proceed.
                        if (i-1 > startPos)
                            tokenList.add(str.substring(startPos, i-1));
                        inStringPrimitive = true;
                        prevWasBackslash = false;
                        startPos = i;
                    }
                } else {
                    if (inStringPrimitive) {
                        // Just keep track of backslashing
                        if (c == '\\') {
                            prevWasBackslash = !prevWasBackslash;
                        }
                    } else {
                        // Some single characters should be broken off as separate tokens.  A space
                        // breaks the token we were just in (if any) but is subsequently discarded.
                        boolean singleCharToken = false;
                        boolean breakCurToken = false;
                        if (c == '(' || c == ')' || c == '{' || c == '}' || c == '=') {
                            singleCharToken = true;
                            breakCurToken = true;
                        } else if (c == ' ' || c == ',') {
                            breakCurToken = true;
                        }

                        // Break current token if necessary
                        if (breakCurToken) {
                            if (i > startPos) {
                                tokenList.add(str.substring(startPos, i));
                            }
                            // Store current character as its own token if needed
                            if (singleCharToken)
                                tokenList.add(String.valueOf(c));
                            startPos = i+1;
                        } else {
                            // Just keep going
                        }
                    }
                }
            }

            // Drop the rest onto the end of tokenList
            if (inStringPrimitive) {
                log.info("bkdb: tokenList:" + tokenList);
                throw new RuntimeException("Unterminated string primitive starting with: "
                        + str.substring(startPos));
            } else {
                if (startPos < str.length())
                    tokenList.add(str.substring(startPos));
            }
            
            Pair<RTWValue, Integer> pair = valueFromString_recur(tokenList, 0);
            if (pair.getRight() < tokenList.size()) {
                StringBuffer sb = new StringBuffer();
                boolean first = true;
                for (int i = pair.getRight(); i < tokenList.size(); i++) {
                    if (first) first = false;
                    else sb.append(", ");
                    sb.append(tokenList.get(i).toString());
                }
                throw new RuntimeException("Extra junk at end of expression: " + sb.toString());
            }
            return pair.getLeft();
        } catch (Exception e) {
            throw new RuntimeException("valueFromString(\"" + str + "\")", e);
        }
    }
}
