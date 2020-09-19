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

    class CacheClass {
        long log_size = 0;
        long log_block = 0;
        long ways = 0;
        long log_sets = 0;
        long log_lines = 0;
        CacheTypes type;
    }

    class ArraysClass {
        String name;
        int dim;
        int elt_size;
        Types type;
        List<Long> dims = new ArrayList<Long>();
        long footprint = 1;
        Boolean isAccessed = false;
        Boolean fitsInCache = true;
        long cacheMisses = 1;
        ArraysClass(String n)
        {
            name = n;
        }
    }

    class LoopAccesses {
        String var;
        int begin = 0;
        long end;
        long stride;
        long iter;

        LoopAccesses(String v)
        {
            var = v;
        }

        void calcIter()
        {
            iter = (end - begin)/stride;
        }
    }

    HashMap<String, ArraysClass> arrays = new HashMap<String,ArraysClass>();

    HashMap<String, Long> lVars = new HashMap<String,Long>();
    
    CacheClass cache = new CacheClass();

    Stack<LoopAccesses> loopStk = new Stack<>();


    // FIXME: Feel free to override additional methods from
    // LoopNestBaseListener.java based on your needs.
    // Method entry callback
    @Override
    public void enterMethodDeclaration(final LoopNestParser.MethodDeclarationContext ctx) {
        System.out.println("enterMethodDeclaration");
    }

    // End of testcase
    @Override
    public void exitMethodDeclarator(final LoopNestParser.MethodDeclaratorContext ctx) {
        System.out.println("exitMethodDeclarator");
    }

    @Override
    public void exitTests(final LoopNestParser.TestsContext ctx) {
        try {
            final FileOutputStream fos = new FileOutputStream("Results.obj");
            final ObjectOutputStream oos = new ObjectOutputStream(fos);
            // FIXME: Serialize your data to a file
            // oos.writeObject();
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
                * Check whether it is a variable or array type and proceed accrodingly
            */
            if(ctx.unannType().unannPrimitiveType() != null)
            {
                String varName = ctx.variableDeclarator().variableDeclaratorId().getText();
                
                switch(varName)
                {
                    case "cachePower" : cache.log_size = Integer.parseInt(ctx.variableDeclarator().literal().getText()); break;
                    case "blockPower" : cache.log_block = Integer.parseInt(ctx.variableDeclarator().literal().getText()); break;
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
                arr.elt_size = typeToSize.get(arr.type);
            
                String d = ctx.unannType().unannArrayType().dims().getText();
                arr.dim = 0;
                switch(d)
                {
                    case "[]": System.out.println("1D"); arr.dim = 1;break;
                    case "[][]": System.out.println("2D"); arr.dim = 2;break;
                    case "[][][]": System.out.println("3D"); arr.dim = 3; break;
                    default : assert(false); break; 
                }

                LoopNestParser.DimExprsContext de = ctx.variableDeclarator().arrayCreationExpression().dimExprs();
                for(int i = arr.dim - 1; i >= 0; i--)
                {
                    if(de.dimExpr(i).IntegerLiteral() != null)
                    {
                        long val = Long.parseLong(de.dimExpr(i).IntegerLiteral().getText());
                        arr.dims.add(val);

                    }
                    else if(de.dimExpr(i).expressionName() != null)
                    {
                        long val = lVars.get(de.dimExpr(i).expressionName().getText());
                        arr.dims.add(val);
                    }
                    else
                    {
                        assert(false);
                    }
                    
                }

                for(int i = 0; i < arr.dim; i++)
                {
                    System.out.println(arr.dims.get(i));
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
        System.out.println(var);
    }
    @Override
    public void exitForCondition(final LoopNestParser.ForConditionContext ctx) {
        assert(ctx.relationalExpression().expressionName(0).getText().equals(loopStk.peek().var));
        if(ctx.relationalExpression().expressionName().size() == 2)
        {
            long s = lVars.get(ctx.relationalExpression().expressionName(1).getText());
            loopStk.peek().end = s;
            System.out.println("End = " + loopStk.peek().end);

        }
        else
        {
            loopStk.peek().end = Long.parseLong(ctx.relationalExpression().IntegerLiteral().getText());
            System.out.println("End = " + loopStk.peek().end);
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
            System.out.println("Stride = " + loopStk.peek().stride);

        }
        else
        {
            loopStk.peek().stride = Long.parseLong(ctx.simplifiedAssignment().IntegerLiteral().getText());
            System.out.println("Stride = " + loopStk.peek().stride);
        }

        loopStk.peek().calcIter();
        System.out.println("Iter = " + loopStk.peek().iter);

    }

    @Override
    public void exitSimplifiedAssignment(final LoopNestParser.SimplifiedAssignmentContext ctx) {
    }

    @Override
    public void exitArrayAccess(final LoopNestParser.ArrayAccessContext ctx) {
        
        String v = loopStk.peek().var;
        System.out.println("Var = " + v);
        //System.out.println(ctx.getText());
        List<String> idx = new ArrayList<>();
        ctx.expressionName().forEach( (x) -> idx.add(x.getText()));
        System.out.println(idx);
        String arr = idx.get(0);
        idx.remove(0);
        Collections.reverse(idx);
        System.out.println(idx.contains(v));

    }

    @Override
    public void exitArrayAccess_lfno_primary(final LoopNestParser.ArrayAccess_lfno_primaryContext ctx) {
        String v = loopStk.peek().var;
        //System.out.println("Var = " + v);
        //System.out.println(ctx.getText());
        List<String> idx = new ArrayList<>();
        ctx.expressionName().forEach( (x) -> idx.add(x.getText()));
        //System.out.println(idx);
        ArraysClass arr = arrays.get(idx.get(0));
        idx.remove(0);
        Collections.reverse(idx);
        //System.out.println(idx.contains(v));
        arr

    }

    @Override
    public void exitForStatement(final LoopNestParser.ForStatementContext ctx) {
        System.out.println("pop : " + loopStk.pop().var);

    }

}
