import beepp.expression.*;
import beepp.util.Pair;
import beepp.util.RangeUnion;
import jebl.evolution.io.NewickImporter;
import jebl.evolution.trees.SimpleRootedTree;
import jebl.evolution.trees.Tree;
import util.FilteredIterable;
import util.Range;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vyacheslav Moklev
 */
public class DummyMain {
    public static void main(String[] args) throws IOException {
        BooleanExpression expr = new IntegerVariable("x", 0, 10).less(new IntegerVariable("y", 5, 5));
        System.out.println(expr.holds());
        /*List<SimpleRootedTree> trees = new ArrayList<>();
        String filePath = "C:\\Users\\slava\\Downloads\\PhyloSAT-master\\PhyloSAT\\data\\simple.tre";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            NewickImporter importer = new NewickImporter(reader, false);
            trees.addAll(importer.importTrees().stream().map(tree -> (SimpleRootedTree) tree).collect(Collectors.toList()));
            reader.close();
        } catch (Exception ignored) {}

        List<PhylogeneticTree> inputTrees = new ArrayList<>();
        for (SimpleRootedTree srt : trees) {
            PhylogeneticTree inputTree = new PhylogeneticTree(srt);
            inputTrees.add(inputTree);
        }

        PrintWriter pw = new PrintWriter(new FileWriter("out.keksik"), true);
        pw.print(new BEEFormulaBuilder(inputTrees, 3, false).build());
        pw.close();*/
    }
}