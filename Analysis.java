import java.util.*;
import static java.util.stream.Collectors.*;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;


// FIXME: You should limit your implementation to this class. You are free to add new auxilliary classes. You do not need to touch the LoopNext.g4 file.
class Analysis extends LoopNestBaseListener {

    // Possible types
    enum Types {
        Byte, Short, Int, Long, Char, Float, Double, Boolean, String
    }

    // Type of variable declaration
    enum VariableType {
        Primitive, Array, Literal
    }

    // Types of caches supported
    enum CacheTypes {
        DirectMapped, SetAssociative, FullyAssociative,
    }

    // auxilliary data-structure for converting strings
    // to types, ignoring strings because string is not a
    // valid type for loop bounds
    final Map<String, Types> stringToType = Collections.unmodifiableMap(new HashMap<String, Types>() {
        private static final long serialVersionUID = 1L;

        {
            put("byte", Types.Byte);
            put("short", Types.Short);
            put("int", Types.Int);
            put("long", Types.Long);
            put("char", Types.Char);
            put("float", Types.Float);
            put("double", Types.Double);
            put("boolean", Types.Boolean);
        }
    });

    // auxilliary data-structure for mapping types to their byte-size
    // size x means the actual size is 2^x bytes, again ignoring strings
    final Map<Types, Integer> typeToSize = Collections.unmodifiableMap(new HashMap<Types, Integer>() {
        private static final long serialVersionUID = 1L;

        {
            put(Types.Byte, 0);
            put(Types.Short, 1);
            put(Types.Int, 2);
            put(Types.Long, 3);
            put(Types.Char, 1);
            put(Types.Float, 2);
            put(Types.Double, 3);
            put(Types.Boolean, 0);
        }
    });

    // Map of cache type string to value of CacheTypes
    final Map<String, CacheTypes> stringToCacheType = Collections.unmodifiableMap(new HashMap<String, CacheTypes>() {
        private static final long serialVersionUID = 1L;

        {
            put("FullyAssociative", CacheTypes.FullyAssociative);
            put("SetAssociative", CacheTypes.SetAssociative);
            put("DirectMapped", CacheTypes.DirectMapped);
        }
    });

    public Analysis() {
    }

    boolean dbg =false;

    class CacheClass {
        long logSize = 0;   //log2 of Cache Size
        long logBlock = 0;  //log2 of Block Size
        long ways = 0;      //#Ways
        long logWays = 0;   //log2 of Ways
        long logSets = 0;   //log2 of Sets
        long logLines = 0;  //log2 of Lines
        long setEnd = 0;    //#bits for set + cache offset
        CacheTypes type;    //Type of Cache
        boolean calcFilled = false; //Values given above computed?

        void fillUp()
        {
            //Calculate the cache parameters from the given information
            if(!calcFilled)
            {
                if(type == CacheTypes.FullyAssociative)
                {
                    logLines = logSize - logBlock;
                    logWays = logLines;
                    logSets = 0;
                    setEnd = logBlock;
                }
                else if(type == CacheTypes.DirectMapped)
                {
                    logLines = logSize - logBlock;
                    ways = 1;
                    logWays = 0;
                    logSets = logLines;
                    setEnd = logSets + logBlock;

                }
                else if(type == CacheTypes.SetAssociative)
                {
                    logLines = logSize - logBlock;
                    logWays = log2(ways);
                    logSets = logLines - logWays;
                    setEnd = logSets + logBlock;

                }
            }
        }

        long log2(long x)
        {
            if(x <= 0) throw new IllegalArgumentException();
            else if(x > 1024) return 10 + log2(x/1024);
            int y = (int) x;
            switch(y)
            {
                case 1 : return 0;
                case 2 : return 1;
                case 4 : return 2;
                case 8 : return 3;
                case 16 : return 4;
                case 32: return 5;
                case 64 : return 6;
                case 128 : return 7;
                case 256 : return 8;
                case 512 : return 9;
                case 1024 : return 10;
                default : return -1;
            }
        }
    }

    class ArraysClass {
        String name;    //Array name
        int dim;        //Array dimensions 1,2,3
        int eltSize;    //Size of an array element
        Types type;     //Type of Array element
        List<Long> dims = new ArrayList<>();    //Array dimensions based on declaration | A[10][20][30] -> [30,20,10]
        List<String> idx = null;    //indices used to index the array in the loop | A[i][j][k] -> [k,j,i]
        Boolean isAccessed = false; //
        Boolean fitsInCache = true; //Does the part of the array accessed so far fit in the cache?
        long cacheMisses = 1;   //Count of Cache Misses
        long varBits = 0;       //Number of address bits after setEnd that can vary
        
        ArraysClass(String n){ name = n;}

        void updateAccess(LoopAccesses loop, CacheClass cache)
        {
            cache.fillUp();
            if(!fitsInCache)
            {
                cacheMisses = cacheMisses * loop.iter;
            }
            else if(idx.indexOf(loop.var) != -1)
            {
                int pos = idx.indexOf(loop.var);

                long addB = eltSize + log2(loop.stride);
                for(int i = 0 ; i < pos; i++)
                {
                    addB += log2(dims.get(i));
                }
                long addE = addB + log2(loop.iter);

                if(dbg){System.out.println(addB + "," + addE);}

                long cacB = cache.logBlock;
                long cacheBlocks = addE - max(addB,cacB); 
                cacheBlocks = max(cacheBlocks,0); //log2 of cache blocks accessed in the loop
                
                if(dbg){System.out.println("CacheBlocks =" + cacheBlocks);}

                cacheMisses *= exp2(cacheBlocks);

                if(dbg){System.out.println("CacheMisses =" + cacheMisses);}

               if(addE > cache.setEnd)
               {
                   varBits += addE - max(addB,cache.setEnd);
                   assert(addE - max(addB,cache.setEnd) >= 0);

                   if(varBits > cache.logWays)
                   {
                       fitsInCache = false;
                   }
               }
            }
        }

        long log2(long x)
        {
            if(x <= 0) throw new IllegalArgumentException();
            else if(x > 1024) return 10 + log2(x/1024);
            int y = (int) x;
            switch(y)
            {
                case 1 : return 0;
                case 2 : return 1;
                case 4 : return 2;
                case 8 : return 3;
                case 16 : return 4;
                case 32: return 5;
                case 64 : return 6;
                case 128 : return 7;
                case 256 : return 8;
                case 512 : return 9;
                case 1024 : return 10;
                default : return -1;
            }
        }
    
        long exp2(long x)
        {
            long res = 1;
            for(int i = 0; i < x; i++)
            {
                res = res*2;
            }
            return res;
        }

        long max(long a, long b)
        {
            if(a > b) return a; else return b;
        }
    }

    class LoopAccesses {
        String var;     //iteration variable
        int begin = 0;  //beginning value
        long end;       //terminal value
        long stride;    
        long iter;      //iterations

        LoopAccesses(String v){var = v;}

        void calcIter() //calculate loop iterations
        {
            iter = (end - begin)/stride;
            if(iter <= 0) iter = 0;
        }
    }

    HashMap<String, ArraysClass> arrays;

    HashMap<String, Long> lVars;    //Store all local Variables in a Symbol Table

    List<HashMap<String,Long>> result = new ArrayList<>();  //Final Result
    
    CacheClass cache;

    Stack<LoopAccesses> loopStk;    //A stack to store the loop nests


    // FIXME: Feel free to override additional methods from
    // LoopNestBaseListener.java based on your needs.
    // Method entry callback
    @Override
    public void enterMethodDeclaration(final LoopNestParser.MethodDeclarationContext ctx) {
        if(dbg){System.out.println("enterMethodDeclaration");}
        cache = new CacheClass();
        loopStk = new Stack<>();
        arrays =  new HashMap<>();
        lVars =  new HashMap<>();
    }

    // End of testcase
    @Override
    public void exitMethodDeclaration(final LoopNestParser.MethodDeclarationContext ctx) {
        if(dbg){System.out.println("exitMethodDeclaration");}
        HashMap<String,Long> cacheMisses= new HashMap<>();
        System.out.println("*********************************************************");
        arrays.forEach((k,v) -> cacheMisses.put(v.name,v.cacheMisses));
        arrays.forEach((k,v) -> System.out.println("Answer: " + v.name + " : " + v.cacheMisses));
        System.out.println("*********************************************************");
        result.add(cacheMisses);
    }

    @Override
    public void exitTests(final LoopNestParser.TestsContext ctx) {
        try {
            final FileOutputStream fos = new FileOutputStream("Results.obj");
            final ObjectOutputStream oos = new ObjectOutputStream(fos);
            // FIXME: Serialize your data to a file
            oos.writeObject(result);
            oos.close();
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void exitLocalVariableDeclaration(final LoopNestParser.LocalVariableDeclarationContext ctx) {

        if(ctx.unannStringType()!= null)
        {
            /*
                * Local Variable being Declared is a string
                * If it is name cacheType, use it accordingly
                * Else do nothing
            */

            String s = ctx.variableDeclarator().variableDeclaratorId().getText();
            if(s.equals("cacheType"))
            {
                cache.type = stringToCacheType.get(ctx.variableDeclarator().literal().getText().replaceAll("^\"|\"$", ""));
            }
        }
        else if(ctx.unannType() != null)
        {
            /*
                * Local Variable being Declared is not a string
                * Check whether it is a primitive variable or array type and proceed accrodingly
            */
            if(ctx.unannType().unannPrimitiveType() != null)
            {
                String varName = ctx.variableDeclarator().variableDeclaratorId().getText();
                
                switch(varName)
                {
                    case "cachePower" : cache.logSize = Integer.parseInt(ctx.variableDeclarator().literal().getText()); break;
                    case "blockPower" : cache.logBlock = Integer.parseInt(ctx.variableDeclarator().literal().getText()); break;
                    case "setSize" : cache.ways =  Integer.parseInt(ctx.variableDeclarator().literal().getText()); break;
                    default : break;
                }

                /*
                    * Checks for local integer type variables and stores them as a long
                    * Floating Types are not stored
                */
                String type = ctx.unannType().unannPrimitiveType().getText();
                switch(type)
                {
                    case "byte":
                    case "short":
                    case "int":
                    case "long":
                    case "char": 
                                String name = ctx.variableDeclarator().variableDeclaratorId().getText();
                                long val = Long.parseLong(ctx.variableDeclarator().literal().getText());
                                lVars.put(name, val);
                                break;
                    default : break;
                }
            }
            else if(ctx.unannType().unannArrayType() != null)
            {
                String name = ctx.variableDeclarator().variableDeclaratorId().getText();
                ArraysClass arr = new ArraysClass(name);

                arrays.put(name,arr);
                
                String t = ctx.unannType().unannArrayType().unannPrimitiveType().getText();
                arr.type = stringToType.get(t);
                arr.eltSize = typeToSize.get(arr.type);
            
                String d = ctx.unannType().unannArrayType().dims().getText();
                arr.dim = 0;
                switch(d)
                {
                    case "[]":  arr.dim = 1; break;
                    case "[][]":  arr.dim = 2; break;
                    case "[][][]":  arr.dim = 3; break;
                    default : assert(false); break; 
                }

                LoopNestParser.DimExprsContext dExpr = ctx.variableDeclarator().arrayCreationExpression().dimExprs();
                for(int i = arr.dim - 1; i >= 0; i--)
                {
                    if(dExpr.dimExpr(i).IntegerLiteral() != null)
                    {
                        long val = Long.parseLong(dExpr.dimExpr(i).IntegerLiteral().getText());
                        arr.dims.add(val);

                    }
                    else if(dExpr.dimExpr(i).expressionName() != null)
                    {
                        long val = lVars.get(dExpr.dimExpr(i).expressionName().getText());
                        arr.dims.add(val);
                    }
                    else
                    {
                        assert(false);
                    }
                }
            }
        }
    }

    @Override
    public void exitVariableDeclarator(final LoopNestParser.VariableDeclaratorContext ctx) {

    }

    @Override
    public void exitArrayCreationExpression(final LoopNestParser.ArrayCreationExpressionContext ctx) {
    }

    @Override
    public void exitDimExprs(final LoopNestParser.DimExprsContext ctx) {
    }

    @Override
    public void exitDimExpr(final LoopNestParser.DimExprContext ctx) {
    }

    @Override
    public void exitLiteral(final LoopNestParser.LiteralContext ctx) {
    }

    @Override
    public void exitVariableDeclaratorId(final LoopNestParser.VariableDeclaratorIdContext ctx) {
    }

    @Override
    public void exitUnannArrayType(final LoopNestParser.UnannArrayTypeContext ctx) {
    }

    @Override
    public void enterDims(final LoopNestParser.DimsContext ctx) {
    }

    @Override
    public void exitUnannPrimitiveType(final LoopNestParser.UnannPrimitiveTypeContext ctx) {
    }

    @Override
    public void exitNumericType(final LoopNestParser.NumericTypeContext ctx) {
    }

    @Override
    public void exitIntegralType(final LoopNestParser.IntegralTypeContext ctx) {
    }

    @Override
    public void exitFloatingPointType(final LoopNestParser.FloatingPointTypeContext ctx) {
    }

    @Override
    public void exitForInit(final LoopNestParser.ForInitContext ctx) {
        String var = ctx.localVariableDeclaration().variableDeclarator().variableDeclaratorId().getText();
        LoopAccesses la = new LoopAccesses(var);
        loopStk.push(la);
        if(dbg){ System.out.println(var);}
    }
    @Override
    public void exitForCondition(final LoopNestParser.ForConditionContext ctx) {
        assert(ctx.relationalExpression().expressionName(0).getText().equals(loopStk.peek().var));
        if(ctx.relationalExpression().expressionName().size() == 2)
        {
            long s = lVars.get(ctx.relationalExpression().expressionName(1).getText());
            loopStk.peek().end = s;
            if(dbg){System.out.println("End = " + loopStk.peek().end);}

        }
        else
        {
            loopStk.peek().end = Long.parseLong(ctx.relationalExpression().IntegerLiteral().getText());
            if(dbg){System.out.println("End = " + loopStk.peek().end);}
        }
    }

    @Override
    public void exitRelationalExpression(final LoopNestParser.RelationalExpressionContext ctx) {
    }

    @Override
    public void exitForUpdate(final LoopNestParser.ForUpdateContext ctx) {
        assert(ctx.simplifiedAssignment().expressionName(0).getText().equals(loopStk.peek().var));
        if(ctx.simplifiedAssignment().expressionName().size() == 2)
        {
            long s = lVars.get(ctx.simplifiedAssignment().expressionName(1).getText());
            loopStk.peek().stride = s;
            if(dbg){System.out.println("Stride = " + loopStk.peek().stride);}

        }
        else
        {
            loopStk.peek().stride = Long.parseLong(ctx.simplifiedAssignment().IntegerLiteral().getText());
            if(dbg){System.out.println("Stride = " + loopStk.peek().stride);}
        }

        loopStk.peek().calcIter();
        if(dbg){System.out.println("Iter = " + loopStk.peek().iter);}

    }

    @Override
    public void exitSimplifiedAssignment(final LoopNestParser.SimplifiedAssignmentContext ctx) {
    }

    @Override
    public void exitArrayAccess(final LoopNestParser.ArrayAccessContext ctx) {
        
        String v = loopStk.peek().var;
        
        if(dbg){System.out.println("Var = " + v);}
        if(dbg){System.out.println(ctx.getText());}
        
        List<String> idx = new ArrayList<>();
        ctx.expressionName().forEach( (x) -> idx.add(x.getText()));
        
        if(dbg){System.out.println(idx);}
        
        ArraysClass arr = arrays.get(idx.get(0));
        
        idx.remove(0);
        Collections.reverse(idx);
        
        if(arr.idx == null){ arr.idx = idx; }
        
        if(dbg){System.out.println(arr.idx);}

    }

    @Override
    public void exitArrayAccess_lfno_primary(final LoopNestParser.ArrayAccess_lfno_primaryContext ctx) {
        String v = loopStk.peek().var;
        
        if(dbg){System.out.println("Var = " + v);}
        if(dbg){System.out.println(ctx.getText());}
        
        List<String> idx = new ArrayList<>();
        ctx.expressionName().forEach( (x) -> idx.add(x.getText()));
        
        if(dbg){System.out.println(idx);}
        
        ArraysClass arr = arrays.get(idx.get(0));
        
        idx.remove(0);
        Collections.reverse(idx);
        if(arr.idx == null){ arr.idx = idx; } 
        
        if(dbg){System.out.println(arr.idx);}
    }

    @Override
    public void exitForStatement(final LoopNestParser.ForStatementContext ctx) {
        LoopAccesses loop = loopStk.pop();
        arrays.forEach( (k,v) -> v.updateAccess(loop,cache));
    }
}
