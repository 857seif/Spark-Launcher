/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.org.json.simple.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.ContainerFactory;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.ParseException;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.Yylex;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.Yytoken;

public class JSONParser {
    public static final int S_INIT = 0;
    public static final int S_IN_FINISHED_VALUE = 1;
    public static final int S_IN_OBJECT = 2;
    public static final int S_IN_ARRAY = 3;
    public static final int S_PASSED_PAIR_KEY = 4;
    public static final int S_IN_ERROR = -1;
    private Yylex lexer = new Yylex(null);
    private Yytoken token = null;
    private int status = 0;

    private int peekStatus(LinkedList<Integer> statusStack) {
        if (statusStack.size() == 0) {
            return -1;
        }
        Integer status = statusStack.getFirst();
        return status;
    }

    public void reset() {
        this.token = null;
        this.status = 0;
    }

    public void reset(Reader in) {
        this.lexer.yyreset(in);
        this.reset();
    }

    public int getPosition() {
        return this.lexer.getPosition();
    }

    public Object parse(String s) throws ParseException {
        return this.parse(s, (ContainerFactory)null);
    }

    public Object parse(String s, ContainerFactory containerFactory) throws ParseException {
        StringReader in = new StringReader(s);
        try {
            return this.parse(in, containerFactory);
        }
        catch (IOException ie) {
            throw new ParseException(-1, 2, ie);
        }
    }

    public Object parse(Reader in) throws IOException, ParseException {
        return this.parse(in, (ContainerFactory)null);
    }

    public Object parse(Reader in, ContainerFactory containerFactory) throws IOException, ParseException {
        this.reset(in);
        LinkedList<Integer> statusStack = new LinkedList<Integer>();
        LinkedList<Object> valueStack = new LinkedList<Object>();
        do {
            this.nextToken();
            block1 : switch (this.status) {
                case 0: {
                    switch (this.token.type) {
                        case 0: {
                            this.status = 1;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(this.token.value);
                            break block1;
                        }
                        case 1: {
                            this.status = 2;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(this.createObjectContainer(containerFactory));
                            break block1;
                        }
                        case 3: {
                            this.status = 3;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(this.createArrayContainer(containerFactory));
                            break block1;
                        }
                    }
                    this.status = -1;
                    break;
                }
                case 1: {
                    if (this.token.type == -1) {
                        return valueStack.removeFirst();
                    }
                    throw new ParseException(this.getPosition(), 1, this.token);
                }
                case 2: {
                    String key;
                    switch (this.token.type) {
                        case 5: {
                            break block1;
                        }
                        case 0: {
                            if (this.token.value instanceof String) {
                                key = (String)this.token.value;
                                valueStack.addFirst(key);
                                this.status = 4;
                                statusStack.addFirst(this.status);
                                break block1;
                            }
                            this.status = -1;
                            break block1;
                        }
                        case 2: {
                            if (valueStack.size() > 1) {
                                statusStack.removeFirst();
                                valueStack.removeFirst();
                                this.status = this.peekStatus(statusStack);
                                break block1;
                            }
                            this.status = 1;
                            break block1;
                        }
                    }
                    this.status = -1;
                    break;
                }
                case 4: {
                    String key;
                    switch (this.token.type) {
                        case 6: {
                            break block1;
                        }
                        case 0: {
                            statusStack.removeFirst();
                            key = (String)valueStack.removeFirst();
                            Map parent = (Map)valueStack.getFirst();
                            parent.put(key, this.token.value);
                            this.status = this.peekStatus(statusStack);
                            break block1;
                        }
                        case 3: {
                            statusStack.removeFirst();
                            key = (String)valueStack.removeFirst();
                            Map parent = (Map)valueStack.getFirst();
                            List<Object> newArray = this.createArrayContainer(containerFactory);
                            parent.put(key, newArray);
                            this.status = 3;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(newArray);
                            break block1;
                        }
                        case 1: {
                            statusStack.removeFirst();
                            key = (String)valueStack.removeFirst();
                            Map parent = (Map)valueStack.getFirst();
                            Map<String, Object> newObject = this.createObjectContainer(containerFactory);
                            parent.put(key, newObject);
                            this.status = 2;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(newObject);
                            break block1;
                        }
                    }
                    this.status = -1;
                    break;
                }
                case 3: {
                    switch (this.token.type) {
                        case 5: {
                            break block1;
                        }
                        case 0: {
                            List val = (List)valueStack.getFirst();
                            val.add(this.token.value);
                            break block1;
                        }
                        case 4: {
                            if (valueStack.size() > 1) {
                                statusStack.removeFirst();
                                valueStack.removeFirst();
                                this.status = this.peekStatus(statusStack);
                                break block1;
                            }
                            this.status = 1;
                            break block1;
                        }
                        case 1: {
                            List val = (List)valueStack.getFirst();
                            Map<String, Object> newObject = this.createObjectContainer(containerFactory);
                            val.add(newObject);
                            this.status = 2;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(newObject);
                            break block1;
                        }
                        case 3: {
                            List val = (List)valueStack.getFirst();
                            List<Object> newArray = this.createArrayContainer(containerFactory);
                            val.add(newArray);
                            this.status = 3;
                            statusStack.addFirst(this.status);
                            valueStack.addFirst(newArray);
                            break block1;
                        }
                    }
                    this.status = -1;
                    break;
                }
                case -1: {
                    throw new ParseException(this.getPosition(), 1, this.token);
                }
            }
            if (this.status != -1) continue;
            throw new ParseException(this.getPosition(), 1, this.token);
        } while (this.token.type != -1);
        throw new ParseException(this.getPosition(), 1, this.token);
    }

    private void nextToken() throws ParseException, IOException {
        this.token = this.lexer.yylex();
        if (this.token == null) {
            this.token = new Yytoken(-1, null);
        }
    }

    private Map<String, Object> createObjectContainer(ContainerFactory containerFactory) {
        if (containerFactory == null) {
            return new JSONObject();
        }
        Map<String, Object> m = containerFactory.createObjectContainer();
        if (m == null) {
            return new JSONObject();
        }
        return m;
    }

    private List<Object> createArrayContainer(ContainerFactory containerFactory) {
        if (containerFactory == null) {
            return new JSONArray();
        }
        List<Object> l = containerFactory.creatArrayContainer();
        if (l == null) {
            return new JSONArray();
        }
        return l;
    }
}

