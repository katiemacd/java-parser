import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.FileInputStream;
import java.io.IOException;

public class StaticAnalyser {
    private detectors detectors = new detectors();

    public StaticAnalyser() {
        this.detectors = new detectors();
    }

    public void analyseFile(FileInputStream file){
        CompilationUnit cu = StaticJavaParser.parse(file);
        detectors.checkLocalVarInit(cu);
        detectors.checkSimpleAssignments(cu);
        detectors.checkOneVarPerDeclaration(cu);
        detectors.checkInstanceVarClassVarAccess(cu);
        detectors.checkLocalDeclarationLevels(cu);
        detectors.checkSwitchFallThrough(cu);
        detectors.checkConstants(cu);
        detectors.checkIgnoreCaughtExceptions(cu);
        detectors.checkLoopIterationVar(cu);
        detectors.checkAccessorMutatorNames(cu);
        detectors.checkSwitchDefaultLabel(cu);
        detectors.checkPrivateMutableReferences(cu);
        detectors.checkExposePrivateMembers(cu);
    }

    public static void main(String[] args) throws IOException {
        StaticAnalyser staticAnalyser = new StaticAnalyser();
        FileInputStream file = new FileInputStream("/Users/scottcunningham/Desktop/squeakyClean.java");
        staticAnalyser.analyseFile(file);
    }
}
