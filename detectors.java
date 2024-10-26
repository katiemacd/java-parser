import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.*;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class detectors {

    // Rule 1: Initialise local variables on declaration
    public void checkLocalVarInit(CompilationUnit cu) {
        // Find all variable declarations in cu
        cu.findAll(VariableDeclarationExpr.class).forEach(variable -> {
            // Loop through each variable in the declaration expression, checking if each has an initializer
            variable.getVariables().forEach(var -> {
                if (var.getInitializer().isPresent()) {
                    System.out.println(" ");  // Placeholder
                } else {
                    // Get variable's line number if available
                    String position = variable.getRange()
                            .map(r -> "Line " + r.begin.line)
                            .orElse("Unknown line");
                    // Print rule number and details for uninitialized variable
                    System.out.println("RULE 1");
                    System.out.println("Variable Name: " + var.getName());
                    System.out.println("Warning: Uninitialized variable on " + position + ".");
                }
            });
        });
    }


    // Rule 2: Keep assignments simple
    public void checkSimpleAssignments(CompilationUnit cu) {
        // Find all assignment expressions in cu
        cu.findAll(AssignExpr.class).forEach(assignExpr -> {
            // Check if the assignment's value is a chained assignment
            if (assignExpr.getValue() instanceof AssignExpr) {
                // Get variable's line number if available
                String position = assignExpr.getRange()
                        .map(r -> "Line " + r.begin.line)
                        .orElse("Unknown line");
                // Print rule number and details of the violation
                System.out.println("RULE 2");
                System.out.println("Violation: Chained assignments found at " + position + ": " + assignExpr);
            }
        });
    }

    // Rule 3: One variable per declaration
    public void checkOneVarPerDeclaration(CompilationUnit cu) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.findAll(VariableDeclarationExpr.class).forEach(vd -> {
                // Check if more than one variable is declared
                if (vd.getVariables().size() > 1) {
                    // Ensure all variables are of the same type
                    boolean allSameType = vd.getVariables().stream()
                            .allMatch(v -> v.getType().equals(vd.getCommonType()));
                    // Ensure all variables are assigned values
                    boolean allAssigned = vd.getVariables().stream()
                            .allMatch(v -> v.getInitializer().isPresent());
                    // If all variables are of the same type and are assigned values, don't flag them
                    if (!allSameType || !allAssigned) {
                        System.out.println("RULE 3");
                        System.out.println("Violation detected in method '" + method.getNameAsString() + "':");
                        System.out.println("Multiple variable declarations without assignments or with different types: " + vd + "\n");
                    }
                }
            });
        });
    }

    // Rule 4: Limit Access to Instance and Class Variables
    public void checkInstanceVarClassVarAccess(CompilationUnit cu) {
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            // Check if field is public
            if (field.hasModifier(Modifier.Keyword.PUBLIC)) {
                for (VariableDeclarator var : field.getVariables()) {
                    // Print rule number and details of violation
                    System.out.println("RULE 4");
                    System.out.println("Warning: Public field breaks encapsulation!\n");
                    System.out.println("Public Field Name: " + var.getName());
                    System.out.println("Type: " + var.getType());
                    field.getRange().ifPresent(range ->
                            System.out.println("Declared at line: " + range.begin.line)
                    );
                }
            }
        });

    }

    // Rule 5: Avoid local declarations that hide declarations at higher levels
    public void checkLocalDeclarationLevels(CompilationUnit cu) {
        // Set to store variable names declared at class level
        Set<String> higherLevelVars = new HashSet<>();
        // Find all class-level declarations
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            // Add each field's variable name to the set of higher-level variables
            field.getVariables().forEach(var -> {
                higherLevelVars.add(var.getNameAsString());
            });
        });
        // Find all method declarations in cu
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            // Find all local variable declarations in each method
            method.findAll(VariableDeclarationExpr.class).forEach(vd -> {
                // Check each local variable in the declaration expression
                vd.getVariables().forEach(var -> {
                    String varName = var.getNameAsString();
                    // Check if variable name matches a class-level variable
                    if (higherLevelVars.contains(varName)) {
                        // Get the line number of the local variable declaration if available
                        String position = vd.getRange()
                                .map(r -> "Line " + r.begin.line)
                                .orElse("Unknown line");
                        // Print rule number and details of the violation
                        System.out.println("RULE 5");
                        System.out.println("Violation: Local variable '" + varName + "' hides higher-level declaration at "
                                + position + ": " + vd);
                    }
                });
            });
        });
    }

    // Rule 6: Switch: Fall-Through is commented
    public void checkSwitchFallThrough(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(SwitchStmt switchStmt, Void arg) {
                super.visit(switchStmt, arg);
                List<SwitchEntry> entries = switchStmt.getEntries();
                // Iterate through each case entry in the switch statement
                for (int i = 0; i < entries.size() - 1; i++) { // Exclude the last entry
                    SwitchEntry entry = entries.get(i);
                    // Check if the case ends with a break or return statement
                    boolean hasBreakOrReturn = entry.getStatements().stream().anyMatch(stmt ->
                            stmt instanceof BreakStmt || stmt instanceof ReturnStmt);
                    // If no break or return, check for a fall-through comment
                    if (!hasBreakOrReturn) {
                        boolean hasFallThroughComment = hasFallThroughComment(entry);
                        // Flag if no fall-through comment is found
                        if (!hasFallThroughComment && !entry.isEmpty()) {
                            System.out.println("fall-through detected without comment in switch statement at line "+ entry.getBegin().map(pos -> pos.line).orElse(-1) + ": " + entry);
                        }
                    }
                }
            }
            // Helper method to check for a "fall-through" comment
            private boolean hasFallThroughComment(SwitchEntry entry) {
                // Check if entry has a fall-through comment
                Optional<Comment> entryComment = entry.getComment();
                boolean commentOnEntry = entryComment.isPresent() && entryComment.get().getContent().trim().equalsIgnoreCase("fall through");
                // If no direct comment on entry, check if last statement has a fall-through comment
                if (!commentOnEntry && !entry.getStatements().isEmpty()) {
                    Optional<Comment> lastStmtComment = entry.getStatements().get(entry.getStatements().size() - 1).getComment();
                    boolean commentOnLastStmt = lastStmtComment.isPresent() && lastStmtComment.get().getContent().trim().equalsIgnoreCase("fall through");
                    return commentOnLastStmt;
                }
                return commentOnEntry;
            }
        }, null);
    }

    // Rule 7: Avoid constants in code
    public static void checkConstants(CompilationUnit cu) {
        // Visit both Integer and String literal expressions
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(IntegerLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Parse the literal value
                int value = Integer.parseInt(n.getValue());
                // Report constants that are not -1, 0, or 1
                if (value != -1 && value != 0 && value != 1) {
                    System.out.println("RULE 7");
                    System.out.println("Warning: Constant value " + value
                            + " at line " + n.getRange().map(r -> r.begin.line).orElse(-1)
                            + " should be a named constant (private static final).");
                }
            }
            @Override
            public void visit(StringLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Report hardcoded string literals
                System.out.println("RULE 7");
                System.out.println("Warning: Hardcoded string \"" + n.getValue()
                        + "\" at line " + n.getRange().map(r -> r.begin.line).orElse(-1)
                        + " should be a named constant (private static final).");
            }
        }, null);
    }

    // Rule 8: Don't ignore caught exceptions
    public void checkIgnoreCaughtExceptions(CompilationUnit cu) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.findAll(TryStmt.class).forEach(tryStmt -> {
                for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                    Parameter param = catchClause.getParameter();
                    String exceptionVarName = param.getNameAsString();
                    // Check if catch block is empty or only contains comments
                    boolean isEmptyOrCommentOnly = catchClause.getBody().getStatements().isEmpty() ||
                            catchClause.getBody().getStatements().stream()
                                    .allMatch(stmt -> stmt.isEmptyStmt() || stmt.getComment().isPresent());
                    // Check if exception variable name contains "expected"
                    boolean isNameValidForEmptyCatch = exceptionVarName.toLowerCase().contains("expected");
                    // Check if there is any comment in the catch block
                    boolean hasComment = !catchClause.getBody().getAllContainedComments().isEmpty();
                    if (!(isEmptyOrCommentOnly && isNameValidForEmptyCatch) && !hasComment) {
                        // Check if exception variable is used in any expression within catch block
                        boolean isUsed = catchClause.getBody().findAll(NameExpr.class).stream()
                                .anyMatch(nameExpr -> nameExpr.getNameAsString().equals(exceptionVarName));
                        if (!isUsed) {
                            // Get exception's line number if available
                            String position = catchClause.getRange()
                                    .map(r -> "Line " + r.begin.line)
                                    .orElse("Unknown line");
                            // Print rule number and details of the violation
                            System.out.println("RULE 8 Violation: Caught exception '" + exceptionVarName +
                                    "' ignored without handling at " + position + ": " + catchClause);
                        }
                    }
                }
            });
        });
    }

    // Rule 9: Don't change a for loop iteration variable in the body of the loop
    public void checkLoopIterationVar(CompilationUnit cu){
        cu.findAll(ForStmt.class).forEach(forStmt -> {
            // Get iteration variable from loops initialisation section
            if (forStmt.getInitialization().isNonEmpty()) {
                forStmt.getInitialization().forEach(init -> {
                    if (init.isVariableDeclarationExpr()) {
                        init.asVariableDeclarationExpr().getVariables().forEach(variable -> {
                            String iterVarName = variable.getNameAsString();
                            // Check loop body for modifications of the iterVaR
                            forStmt.getBody().findAll(AssignExpr.class).forEach(assignExpr -> {
                                Expression target = assignExpr.getTarget();
                                if (target.isNameExpr() && target.asNameExpr().getNameAsString().equals(iterVarName)) {
                                    // Print rule number and details of the violation
                                    System.out.println("RULE 9");
                                    System.out.println("Violation: iteration variable '" + iterVarName +
                                            "' is modified in the loop body at line " + assignExpr.getBegin().map(pos -> pos.line).orElse(-1));
                                }
                            });

                            // Check for unary expressions
                            forStmt.getBody().findAll(UnaryExpr.class).forEach(unaryExpr -> {
                                if (unaryExpr.getExpression().isNameExpr()
                                        && unaryExpr.getExpression().asNameExpr().getNameAsString().equals(iterVarName)
                                        && (unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                                        || unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT
                                        || unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT
                                        || unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT)) {

                                    System.out.println("RULE 9");
                                    System.out.println("Violation: iteration variable '" + iterVarName +
                                            "' is modified with a unary operation (++ or --) in the loop body at line " + unaryExpr.getBegin().map(pos -> pos.line).orElse(-1));
                                }
                            });
                        });
                    }
                });
            }
        });

    }

    // Rule 10: Accessors and Mutators should be named appropriately
    public static void checkAccessorMutatorNames(CompilationUnit cu) {
        // Check all methods for accessor/mutator logic and naming conventions
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                String methodName = method.getNameAsString();
                int parameterCount = method.getParameters().size();
                // Analyze method body to determine if it's an accessor or mutator
                boolean isAccessor = isAccessorMethod(method);
                boolean isMutator = isMutatorMethod(method);
                // Check if method starts with "get" or "set", followed by a capital letter
                if (isAccessor && !methodName.matches("get[A-Z].*")) {
                    System.out.println("RULE 10");
                    System.out.println("Warning: Method '" + methodName + "' appears to be a getter but does not follow the 'get<VariableName>' pattern.");
                }
                if (isMutator && !methodName.matches("set[A-Z].*")) {
                    System.out.println("RULE 10");
                    System.out.println("Warning: Method '" + methodName + "' appears to be a setter but does not follow the 'set<VariableName>' pattern.");
                }
            }
        }, null);
    }

    // Helper method to check if a method is an accessor (getter)
    private static boolean isAccessorMethod(MethodDeclaration method) {
        // Accessor methods should have no parameters and return a field
        if (method.getParameters().isEmpty() && method.getType() != null && method.getBody().isPresent()) {
            BlockStmt body = method.getBody().get();
            for (Statement stmt : body.getStatements()) {
                if (stmt.isReturnStmt()) {
                    ReturnStmt returnStmt = stmt.asReturnStmt();
                    if (returnStmt.getExpression().isPresent() && returnStmt.getExpression().get().isNameExpr()) {
                        // It's an accessor if it returns a field
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Helper method to check if a method is a mutator (setter)
    private static boolean isMutatorMethod(MethodDeclaration method) {
        // Mutator methods should have exactly one parameter and modify a field
        if (method.getParameters().size() == 1 && method.getBody().isPresent()) {
            BlockStmt body = method.getBody().get();
            for (Statement stmt : body.getStatements()) {
                if (stmt.isExpressionStmt()) {
                    Expression expr = stmt.asExpressionStmt().getExpression();
                    if (expr.isAssignExpr()) {
                        AssignExpr assignExpr = expr.asAssignExpr();
                        // It's a mutator if it assigns a value to a field (usually the field)
                        if (assignExpr.getTarget().isFieldAccessExpr() || assignExpr.getTarget().isNameExpr()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Rule 11: Switch: default label is included
    public void checkSwitchDefaultLabel(CompilationUnit cu) {
        // Find all switch statements
        cu.findAll(SwitchStmt.class).forEach(switchStmt -> {
            // Get all cases in switch statement
            boolean hasDefaultCase = switchStmt.getEntries().stream().anyMatch(entry -> {
                // Default case (entry with no labels)
                return entry.getLabels().isEmpty();
            });
            if (!hasDefaultCase) {
                System.out.println("RULE 11");
                System.out.println("Violation: switch statement at line " +
                        switchStmt.getBegin().map(pos -> pos.line).orElse(-1) +
                        " does not include a default case");
            }
        });
    }

    // Rule 12: Do not return references to private mutable class members
    public void checkPrivateMutableReferences(CompilationUnit cu) {
        // Find class declarations
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Find private fields
            classDecl.findAll(FieldDeclaration.class).forEach(field -> {
                if (field.isPrivate() && field.getElementType() instanceof ClassOrInterfaceType) {
                    String fieldType = field.getElementType().asString();  // Get field type
                    // Check methods for getters returning this field
                    field.getVariables().forEach(variable -> {
                        String fieldName = variable.getNameAsString();
                        classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                            method.findAll(ReturnStmt.class).forEach(returnStmt -> {
                                if (returnStmt.getExpression().isPresent() &&
                                        returnStmt.getExpression().get().isNameExpr() &&
                                        returnStmt.getExpression().get().asNameExpr().getNameAsString().equals(fieldName)) {
                                    // Print rule number and details of violation
                                    System.out.println("RULE 12");
                                    System.out.println("Violation: Method '" + method.getNameAsString() +
                                            "' returns a direct reference to private mutable field '" + fieldName +
                                            "' (type: " + fieldType + ") in class '" + classDecl.getNameAsString() +
                                            "' at line " + returnStmt.getBegin().map(pos -> pos.line).orElse(-1));
                                }
                            });
                        });
                    });
                }
            });
        });
    }

    // Rule 13: Do not expose private members of an outer class from within a nested class
    public void checkExposePrivateMembers(CompilationUnit cu) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(outerClass -> {
            outerClass.findAll(FieldDeclaration.class).forEach(field -> {
                if (field.isPrivate()) {
                    String fieldName = field.getVariables().get(0).getNameAsString();
                    outerClass.getMembers().stream()
                            .filter(member -> member instanceof ClassOrInterfaceDeclaration)
                            .map(member -> (ClassOrInterfaceDeclaration) member)
                            .forEach(nestedClass -> {
                                // Only check if nested class is not private
                                if (!nestedClass.isPrivate()) {
                                    nestedClass.findAll(MethodDeclaration.class).forEach(method -> {
                                        if (method.isPublic()) {
                                            method.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
                                                String accessedField = fieldAccess.getNameAsString();
                                                if (accessedField.equals(fieldName)) {
                                                    printViolation(outerClass, nestedClass, method, fieldName);
                                                }
                                            });

                                            method.findAll(NameExpr.class).forEach(nameExpr -> {
                                                if (nameExpr.getNameAsString().equals(fieldName)) {
                                                    printViolation(outerClass, nestedClass, method, fieldName);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                }
            });
        });
    }

    // Helper function to print violations
    private void printViolation(ClassOrInterfaceDeclaration outerClass, ClassOrInterfaceDeclaration nestedClass, MethodDeclaration method, String fieldName) {
        System.out.println("RULE 13");
        System.out.println("Violation: Nested class '" + nestedClass.getNameAsString() +
                "' exposes private field '" + fieldName +
                "' of outer class '" + outerClass.getNameAsString() +
                "' through public method '" + method.getNameAsString() +
                "' at line " + method.getBegin().map(pos -> pos.line).orElse(-1));
    }
}
