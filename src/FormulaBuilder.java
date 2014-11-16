import jebl.evolution.graphs.Node;
import jebl.evolution.taxa.Taxon;
import jebl.evolution.trees.SimpleRootedTree;

import java.util.*;

/**
 * Vladimir Ulyantsev
 * Date: 24.04.13
 * Time: 21:01
 */
public class FormulaBuilder {
    private int n;

    private int k;

    private int treeNodesCount;

    private List<PhylogeneticTree> phTrees;

    private Map<String, Integer> m;

    private StringBuilder sb;

    private boolean enableReticulationConnection;

    private boolean disableComments;

    private int clausesCount;

    public FormulaBuilder(List<PhylogeneticTree> trees, int hybridisationNumber, Map<String, Integer> translationMap) {
        this(trees, hybridisationNumber, translationMap, false, false);
    }

    public FormulaBuilder(List<PhylogeneticTree> trees,
                          int hybridisationNumber,
                          Map<String, Integer> translationMap,
                          boolean enableReticulationConnection,
                          boolean disableComments) {
        this.phTrees = trees;

        this.k = hybridisationNumber;
        this.m = translationMap;

        this.n = phTrees.get(0).getTaxaSize();
        this.treeNodesCount = 2 * n - 1 + k;
        this.enableReticulationConnection = enableReticulationConnection;
        this.disableComments = disableComments;
        this.clausesCount = 0;
    }

    public String buildCNF() {
        if (this.m.size() > 0) {
            throw new RuntimeException("Given translation map (variable -> int) is not empty");
        }

        this.sb = new StringBuilder();
        commentCNF("n = %d; k = %d; trees count = %d", n, k, phTrees.size());

        addParentConstraints();
        addLeftRightConstraints();
        addReticulationChildConstraints();
        addReticulationParentConstraints();
        addChildParentConstraints();

        for (int treeNumber = 0; treeNumber < phTrees.size(); treeNumber++) {
            addDirUsedConstraints(treeNumber);
            if (this.enableReticulationConnection) {
                addRUsedConstraints(treeNumber);
            }
            addUpConstraints(treeNumber);
            addXConstraints(treeNumber);
            addDataConstraints(treeNumber);
        }

        for (int treeNumber = 0; treeNumber < phTrees.size(); treeNumber++) {
            for (int otherTree = 0; otherTree < phTrees.size(); otherTree++) {
                if (treeNumber != otherTree) {
                    addConstraintsForPairOfTrees(treeNumber, otherTree);
                }
            }
        }

        String CNFProperties = String.format("p cnf %d %d\n", this.m.size(), this.clausesCount);
        this.sb.insert(0, CNFProperties);

        return this.sb.toString();
    }

    private void addParentConstraints() {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber = 0; nodeNumber < treeNodesCount - 1; nodeNumber++) {
                for (int parentNumber : possibleParents(nodeNumber)) {
                    createVar("parent", nodeNumber, parentNumber);
                }
            }
            commentCNF("Variables parent_v_u is in [%d, %d]", intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for parent_v_u");
        for (int nodeNumber = 0; nodeNumber < treeNodesCount - 1; nodeNumber++) {
            String atLeastOneParent = "";
            for (int parentNumber : possibleParents(nodeNumber)) {
                atLeastOneParent += getVar("parent", nodeNumber, parentNumber) + " ";
            }
            addClause(atLeastOneParent);
        }

        commentCNF("At-most-one constraints for parent_v_u");
        for (int nodeNumber = 0; nodeNumber < treeNodesCount - 1; nodeNumber++) {
            for (int parentNumber : possibleParents(nodeNumber)) {
                for (int otherParentNumber : possibleParents(nodeNumber)) {
                    if (otherParentNumber > parentNumber) {
                        addClause(-getVar("parent", nodeNumber, parentNumber),
                                -getVar("parent", nodeNumber, otherParentNumber));
                    }
                }
            }
        }
    }

    private void addLeftRightConstraints() {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber = n; nodeNumber < treeNodesCount; nodeNumber++) {
                for (int childNumber : possibleChildren(nodeNumber)) {
                    createVar("left", nodeNumber, childNumber);
                }
            }
            commentCNF("Variables left_v_u is in [%d, %d]", intervalStart, m.size());

            intervalStart = m.size() + 1;
            for (int nodeNumber = n; nodeNumber < treeNodesCount; nodeNumber++) {
                for (int childNumber : possibleChildren(nodeNumber)) {
                    createVar("right", nodeNumber, childNumber);
                }
            }
            commentCNF("Variables right_v_u is in [%d, %d]", intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for left_v_u and right_v_u");
        for (int nodeNumber = n; nodeNumber < treeNodesCount; nodeNumber++) {
            String atLeastOneLeft = "", atLeastOneRight = "";
            for (int childNumber : possibleChildren(nodeNumber)) {
                atLeastOneLeft += getVar("left", nodeNumber, childNumber) + " ";
                atLeastOneRight += getVar("right", nodeNumber, childNumber) + " ";
            }
            addClause(atLeastOneLeft);
            addClause(atLeastOneRight);
        }

        commentCNF("At-most-one constraints for left_v_u and right_v_u");
        commentCNF("Also, constraints for left_v_u < right_v_u");
        for (int nodeNumber = n; nodeNumber < treeNodesCount; nodeNumber++) {
            for (int childNumber : possibleChildren(nodeNumber)) {
                for (int otherNumber : possibleChildren(nodeNumber)) {
                    if (childNumber < otherNumber) {
                        addClause(-getVar("left", nodeNumber, childNumber),
                                -getVar("left", nodeNumber, otherNumber));
                        addClause(-getVar("right", nodeNumber, childNumber),
                                -getVar("right", nodeNumber, otherNumber));
                    }
 // TODO: sdf
//                    if (childNumber <= otherNumber) {
//                        addClause(-getVar("right", nodeNumber, childNumber),
//                                -getVar("left", nodeNumber, otherNumber));
//                    }
                }
            }
        }
    }

    private void addReticulationChildConstraints() {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber : reticulationNodes()) {
                for (int childNumber : possibleChildren(nodeNumber)) {
                    createVar("ch", nodeNumber, childNumber);
                }
            }
            commentCNF("Variables ch_v_u is in [%d, %d]", intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for ch_v_u");
        for (int nodeNumber : reticulationNodes()) {
            String atLeastOne = "";
            for (int childNumber : possibleChildren(nodeNumber)) {
                atLeastOne += getVar("ch", nodeNumber, childNumber) + " ";
            }
            addClause(atLeastOne);
        }

        commentCNF("At-most-one constraints for ch_v_u");
        for (int nodeNumber : reticulationNodes()) {
            for (int childNumber : possibleChildren(nodeNumber)) {
                for (int otherNumber : possibleChildren(nodeNumber)) {
                    if (childNumber < otherNumber) {
                        addClause(-getVar("ch", nodeNumber, childNumber), -getVar("ch", nodeNumber, otherNumber));
                    }
                }
            }
        }
    }

    private void addReticulationParentConstraints() {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber : reticulationNodes()) {
                for (int parentNumber : possibleParents(nodeNumber)) {
                    createVar("lp", nodeNumber, parentNumber);
                }
            }
            commentCNF("Variables lp_v_u is in [%d, %d]", intervalStart, m.size());

            intervalStart = m.size() + 1;
            for (int nodeNumber : reticulationNodes()) {
                for (int parentNumber : possibleParents(nodeNumber)) {
                    createVar("rp", nodeNumber, parentNumber);
                }
            }
            commentCNF("Variables rp_v_u is in [%d, %d]", intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for lp_v_u and rp_v_u");
        for (int nodeNumber : reticulationNodes()) {
            String atLeastOneLeft = "", atLeastOneRight = "";
            for (int parentNumber : possibleParents(nodeNumber)) {
                atLeastOneLeft += getVar("lp", nodeNumber, parentNumber) + " ";
                atLeastOneRight += getVar("rp", nodeNumber, parentNumber) + " ";
            }
            addClause(atLeastOneLeft);
            addClause(atLeastOneRight);
        }

        commentCNF("At-most-one constraints for lp_v_u and rp_v_u");
        commentCNF("Also, constraints for lp_v_u < rp_v_u");
        for (int nodeNumber : reticulationNodes()) {
            for (int parent : possibleParents(nodeNumber)) {
                for (int otherParent : possibleParents(nodeNumber)) {
                    int lpVar = getVar("lp", nodeNumber, parent);
                    int otherLpVar = getVar("lp", nodeNumber, otherParent);
                    int rpVar = getVar("rp", nodeNumber, parent);
                    int otherRpVar = getVar("rp", nodeNumber, otherParent);

                    if (parent < otherParent) {
                        addClause(-lpVar, -otherLpVar);
                        addClause(-rpVar, -otherRpVar);
                    }
 // TODO: uncomment
//                    if (parent <= otherParent) {
//                        addClause(-rpVar, -otherLpVar);
//                    }
                }
            }
        }
    }

    private void addChildParentConstraints() {
        commentCNF("Constraints which connect tree nodes left_v_u, right_v_u with children's parents vars");
        for (int nodeNumber : treeNodes()) {
            for (int childNumber : possibleChildren(nodeNumber)) {
                int leftVar = getVar("left", nodeNumber, childNumber);
                int rightVar = getVar("right", nodeNumber, childNumber);

                if (childNumber < treeNodesCount) {
                    int parentVar = getVar("parent", childNumber, nodeNumber);

                    addClause(-leftVar, parentVar); // if LEFT then PARENT
                    addClause(-rightVar, parentVar); // if RIGHT then PARENT
                    addClause(-parentVar, leftVar, rightVar); // if PARENT then LEFT or RIGHT
                } else {
                    int lpVar = getVar("lp", childNumber, nodeNumber);
                    int rpVar = getVar("rp", childNumber, nodeNumber);

                    addClause(-leftVar, lpVar, rpVar); // if LEFT then LP or RP
                    addClause(-rightVar, lpVar, rpVar); // if RIGHT then LP or RP
                    addClause(-lpVar, leftVar, rightVar); // if LP then LEFT or RIGHT
                    addClause(-rpVar, leftVar, rightVar); // if RP then LEFT or RIGHT
                }
            }
        }

        commentCNF("Constraints which connect reticulation nodes ch_v_u with children's parents vars");
        for (int nodeNumber : reticulationNodes()) {
            for (int childNumber : possibleChildren(nodeNumber)) {
                int chVar = getVar("ch", nodeNumber, childNumber);

                if (childNumber < treeNodesCount) {
                    int parentVar = getVar("parent", childNumber, nodeNumber);

                    addClause(-chVar, parentVar); // if CH then PARENT
                    addClause(-parentVar, chVar); // if PARENT then CH
                } else {
                    int lpVar = m.get("lp_" + childNumber + "_" + nodeNumber);
                    int rpVar = m.get("rp_" + childNumber + "_" + nodeNumber);

                    addClause(-lpVar, chVar); // if LP then CH
                    addClause(-rpVar, chVar); // if RP then CH
                    addClause(-chVar, lpVar, rpVar); // if CH then LP or RP
                }
            }
        }

        commentCNF("Constraints for ordering ch_v_u and lp_v_u, rp_v_u, connected with reticulation nodes");
        for (int nodeNumber : reticulationNodes()) {
            for (int childNumber : possibleChildren(nodeNumber)) {
                if (childNumber < treeNodesCount) {
                    int chVar = getVar("ch", nodeNumber, childNumber);

                    for (int parentNumber = n; parentNumber <= childNumber; parentNumber++) {
                        int lpVar = getVar("lp", nodeNumber, parentNumber);
                        int rpVar = getVar("rp", nodeNumber, parentNumber);

                        addClause(-chVar, -lpVar); // CHILD less then LP
                        addClause(-chVar, -rpVar); // CHILD less then RP
                    }
                }
            }
        }
    }

    private void addDirUsedConstraints(int treeNumber) {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber : reticulationNodes()) {
                createVar("dir", treeNumber, nodeNumber);
            }
            commentCNF("Variables dir_%d_v is in [%d, %d]", treeNumber, intervalStart, m.size());

            intervalStart = m.size() + 1;
            for (int nodeNumber : treeNodes()) {
                createVar("used", treeNumber, nodeNumber);
            }
            commentCNF("Variables used_%d_v is in [%d, %d]", treeNumber, intervalStart, m.size());
        }

        commentCNF("(not dir => not used) constraints");
        for (int nodeNumber : reticulationNodes()) {
            for (int parentNumber : possibleParents(nodeNumber)) {
                int dirVar = getVar("dir", treeNumber, nodeNumber);
                int lpVar = getVar("lp", nodeNumber, parentNumber);
                int rpVar = getVar("rp", nodeNumber, parentNumber);

                if (parentNumber < treeNodesCount) {
                    int usedVar = getVar("used", treeNumber, parentNumber);

                    addClause(dirVar, -lpVar, -usedVar); // if ~DIR and LP then ~USED
                    addClause(-dirVar, -rpVar, -usedVar); // if DIR and RP then ~USED
                }
            }
        }
    }

    private void addRUsedConstraints(int treeNumber) {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber : reticulationNodes()) {
                createVar("rused", treeNumber, nodeNumber);
            }
            commentCNF("Variables rused_%d_v is in [%d, %d]", treeNumber, intervalStart, m.size());
        }

        commentCNF("RUsed constraints");
        for (int nodeNumber : reticulationNodes()) {
            int rusedVar = getVar("rused", treeNumber, nodeNumber);

            for (int childNumber : possibleChildren(nodeNumber)) {
                int chVar = getVar("ch", nodeNumber, childNumber);

                if (childNumber < treeNodesCount) {
                    addClause(-chVar, rusedVar); // if CH is tree node then RUSED
                } else {
                    int childRusedVar = getVar("rused", treeNumber, childNumber);
                    int childDirVar = getVar("dir", treeNumber, childNumber);
                    int lpVar = getVar("lp", childNumber, nodeNumber);
                    int rpVar = getVar("rp", childNumber, nodeNumber);

                    addClause(-chVar, childRusedVar, -rusedVar); // if CH not RUSED then ~RUSED

                    addClause(-lpVar, childDirVar, -rusedVar); // if child LP and ~DIR then ~RUSED
                    // if child LP and DIR and child RUSED then RUSED
                    addClause(-lpVar, -childDirVar, -childRusedVar, rusedVar);

                    // same for RP but DIR
                    addClause(-rpVar, -childDirVar, -rusedVar);
                    addClause(-rpVar, childDirVar, -childRusedVar, rusedVar);
                }
            }
        }

        commentCNF("Connect RUsed with tree nodes Used");
        for (int nodeNumber : reticulationNodes()) {
            int rusedVar = getVar("rused", treeNumber, nodeNumber);

            for (int parentNumber : possibleParents(nodeNumber)) {
                int lpVar = getVar("lp", nodeNumber, parentNumber);
                int rpVar = getVar("rp", nodeNumber, parentNumber);

                if (parentNumber < treeNodesCount) {
                    int usedVar = m.get("used_" + treeNumber + "_" + parentNumber);

                    addClause(-lpVar, rusedVar, -usedVar); // LP and ~RUSED then ~USED
                    addClause(-rpVar, rusedVar, -usedVar); // RP and ~RUSED then ~USED
                }
            }
        }
    }

    private void addUpConstraints(int treeNumber) {
        {
            int intervalStart = m.size() + 1;
            for (int nodeNumber : allNodes()) {
                for (int up : possibleUp(nodeNumber)) {
                    createVar("up", treeNumber, nodeNumber, up);
                }
            }
            commentCNF("Variables up_%d_v_'u is in [%d, %d]", treeNumber, intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for up_%d_v_u", treeNumber);
        for (int nodeNumber : allNodes()) {
            if (nodeNumber != treeNodesCount - 1) {
                String atLeastOne = "";
                for (int up : possibleUp(nodeNumber)) {
                    atLeastOne += getVar("up", treeNumber, nodeNumber, up) + " ";
                }
                addClause(atLeastOne);
            }
        }

        commentCNF("At-most-one constraints for up_%d_v_u", treeNumber);
        for (int nodeNumber : allNodes()) {
            for (int up : possibleUp(nodeNumber)) {
                for (int otherUp : possibleUp(nodeNumber)) {
                    if (up < otherUp) {
                        addClause(-getVar("up", treeNumber, nodeNumber, up),
                                -getVar("up", treeNumber, nodeNumber, otherUp));
                    }
                }
            }
        }

        commentCNF("Connect up_%d_v_u with parent_v_u and used_%d_v (tree nodes)", treeNumber, treeNumber);
        for (int nodeNumber = 0; nodeNumber < treeNodesCount; nodeNumber++) {
            for (int parent : possibleParents(nodeNumber)) {
                int parentVar = getVar("parent", nodeNumber, parent);

                if (parent < treeNodesCount) {
                    int upVar = getVar("up", treeNumber, nodeNumber, parent);
                    int usedVar = getVar("used", treeNumber, parent);

                    // if parent is used
                    {
                        addClause(-parentVar, -usedVar, upVar); // if PARENT and USED then UP
                        addClause(-parentVar, -upVar, usedVar); // if PARENT and UP then USED
                    }

                    // if parent is not used
                    for (int parentUp : possibleUp(parent)) {
                        int nodeUpVar = getVar("up", treeNumber, nodeNumber, parentUp);
                        int parentUpVar = getVar("up", treeNumber, parent, parentUp);

                        addClause(-parentVar, usedVar, -parentUpVar, nodeUpVar); // if tree PARENT and ~USED
                        addClause(-parentVar, usedVar, -nodeUpVar, parentUpVar); // set PARENT UP
                    }
                } else {
                    for (int parentUp : possibleUp(parent)) {
                        int parentUpVar = getVar("up", treeNumber, parent, parentUp);

                        if (parentUp <= nodeNumber) {
                            addClause(-parentVar, -parentUpVar);
                        } else {
                            int nodeUpVar = getVar("up", treeNumber, nodeNumber, parentUp);
                            addClause(-parentVar, -parentUpVar, nodeUpVar);

                            // здесь можно добавить еще ограничение
                        }
                    }
                }
            }
        }

        commentCNF("Connect up_%d_v_u with parent_v_u and used_%d_v (network nodes)", treeNumber, treeNumber);
        for (int nodeNumber : reticulationNodes()) {
            int dirVar = getVar("dir", treeNumber, nodeNumber);

            for (int parent : possibleParents(nodeNumber)) {
                int lpVar = getVar("lp", nodeNumber, parent);
                int rpVar = getVar("rp", nodeNumber, parent);

                if (parent < treeNodesCount) {
                    int parentUsedVar = getVar("used", treeNumber, parent);
                    int upVar = getVar("up", treeNumber, nodeNumber, parent);
                    // if parent is used and up
                    {
                        addClause(-lpVar, -dirVar, -parentUsedVar, upVar); // LP and USED then UP
                        addClause(-rpVar, dirVar, -parentUsedVar, upVar); // RP and USED then UP
                    }

                    // if parent is not used
                    for (int parentUp : possibleUp(parent)) {
                        int parentUpVar = getVar("up", treeNumber, parent, parentUp);
                        int nodeUpVar = getVar("up", treeNumber, nodeNumber, parentUp);

                        // PARENT is LP and ~UP
                        addClause(-lpVar, -dirVar, parentUsedVar, -parentUpVar, nodeUpVar);

                        // PARENT is RP and ~UP
                        addClause(-rpVar, dirVar, parentUsedVar, -parentUpVar, nodeUpVar);
                    }
                } else {
                    for (int parentUp : possibleUp(parent)) {
                        // parent is reticulation
                        int parentUpVar = getVar("up", treeNumber, parent, parentUp);
                        int nodeUpVar = getVar("up", treeNumber, nodeNumber, parentUp);

                        addClause(-lpVar, -dirVar, -parentUpVar, nodeUpVar);
                        addClause(-rpVar, dirVar, -parentUpVar, nodeUpVar);
                    }
                }
            }
        }
    }

    private void addXConstraints(int treeNumber) {
        {
            int intervalStart = m.size() + 1;
            for (int treeNodeNumber = n; treeNodeNumber < 2 * n - 1; treeNodeNumber++) {
                for (int nodeNumber : treeNodes()) {
                    createVar("x", treeNumber, treeNodeNumber, nodeNumber);
                }
            }
            commentCNF("Variables x_%d_tv_v is in [%d, %d]", treeNumber, intervalStart, m.size());
        }

        commentCNF("At-least-one constraints for x_%d_tv_v", treeNumber);
        for (int treeNodeNumber = n; treeNodeNumber < 2 * n - 1; treeNodeNumber++) {
            String atLeastOne = "";
            for (int nodeNumber : treeNodes()) {
                atLeastOne += getVar("x", treeNumber, treeNodeNumber, nodeNumber) + " ";
            }
            addClause(atLeastOne);
        }

        commentCNF("At-most-one constraints for x_" + treeNumber + "_tv_v");
        for (int treeNodeNumber = n; treeNodeNumber < 2 * n - 1; treeNodeNumber++) {
            for (int nodeNumber : treeNodes()) {
                for (int otherNode : treeNodes()) {
                    if (nodeNumber < otherNode) {
                        addClause(-getVar("x", treeNumber, treeNodeNumber, nodeNumber),
                                -getVar("x", treeNumber, treeNodeNumber, otherNode));
                    }
                }
            }
        }

        commentCNF("At-most-one x_%d_tv_v points to v", treeNumber);
        for (int treeNodeNumber = n; treeNodeNumber < 2 * n - 1; treeNodeNumber++) {
            for (int otherTreeNode = n; otherTreeNode < treeNodeNumber; otherTreeNode++) {
                for (int nodeNumber : treeNodes()) {
                    addClause(-getVar("x", treeNumber, treeNodeNumber, nodeNumber),
                            -getVar("x", treeNumber, otherTreeNode, nodeNumber));
                }
            }
        }

        commentCNF("If x_%d_tv_v then used_%d_v", treeNumber, treeNumber);
        for (int treeNodeNumber = n; treeNodeNumber < 2 * n - 1; treeNodeNumber++) {
            for (int nodeNumber : treeNodes()) {
                // X means USED
                addClause(-getVar("x", treeNumber, treeNodeNumber, nodeNumber),
                        getVar("used", treeNumber, nodeNumber));
            }
        }
    }

    private void addDataConstraints(int treeNumber) {
        PhylogeneticTree phTree = this.phTrees.get(treeNumber);

        commentCNF("Data constraints for tree %d: connect X and UP variables", treeNumber);
        for (int treeNodeNumber = 0; treeNodeNumber < phTree.size(); treeNodeNumber++) {
            int treeParentNumber = phTree.getParent(treeNodeNumber);
            if (treeParentNumber == -1) {
                addClause(getVar("x", treeNumber, treeNodeNumber, treeNodesCount - 1)); // root to root
                continue;
            }

            if (treeNodeNumber < n) {
                for (int parentNodeNumber : treeNodes()) {
                    int parentXVar = getVar("x", treeNumber, treeParentNumber, parentNodeNumber);
                    int taxonUpVar = getVar("up", treeNumber, treeNodeNumber, parentNodeNumber);
                    addClause(-parentXVar, taxonUpVar);
                }
            } else {
                for (int nodeNumber : treeNodes()) {
                    int xVar = getVar("x", treeNumber, treeNodeNumber, nodeNumber);

                    for (int parentNodeNumber : possibleUp(nodeNumber)) {
                        int parentXVar = getVar("x", treeNumber, treeParentNumber, parentNodeNumber);
                        int upVar = getVar("up", treeNumber, nodeNumber, parentNodeNumber);

                        addClause(-xVar, -parentXVar, upVar);
                        addClause(-xVar, -upVar, parentXVar);

                        // This constraint is wrong, because there can be two such nodes in the tree
                        //String parentXAndUpThenX = "-" + upVar + " -" + parentXVar + " " + xVar + " 0\n";
                        //sb.append(parentXAndUpThenX);

                    }

                    for (int parentNodeNumber : treeNodes()) {
                        int parentXVar = m.get("x_" + treeNumber + "_" + treeParentNumber + "_" + parentNodeNumber);
                        if (parentNodeNumber < nodeNumber) {
                            addClause(-xVar, -parentXVar);
                        }
                    }
                }
            }
        }

        commentCNF("Constraints connected with tree nodes depth and subtree sizes (heap structure)");
        for (int treeNodeNumber = n; treeNodeNumber < phTree.size() - 1; treeNodeNumber++) {
            int subtreeNonLeafCount = phTree.getSubtreeSize(treeNodeNumber) / 2 - 1;
            for (int nodeNumber = n; nodeNumber < n + subtreeNonLeafCount; nodeNumber++) {
                addClause(-getVar("x", treeNumber, treeNodeNumber, nodeNumber));
            }
            for (int nodeNumber = treeNodesCount - phTree.getDepth(treeNodeNumber);
                 nodeNumber < treeNodesCount; nodeNumber++) {
                addClause(-getVar("x", treeNumber, treeNodeNumber, nodeNumber));
            }
        }

    }

    private void addConstraintsForPairOfTrees(int t1, int t2) {
        PhylogeneticTree phTree1 = this.phTrees.get(t1);
        PhylogeneticTree phTree2 = this.phTrees.get(t2);

        int totalEquals = 0, totalDifferent = 0;
        for (int node1 = n; node1 < 2 * n - 2; node1++) {
            List<Integer> taxa1 = phTree1.getTaxa(node1);
            for (int node2 = n; node2 < 2 * n - 2; node2++) {
                List<Integer> taxa2 = phTree2.getTaxa(node2);

                boolean isEquals = taxa1.size() == taxa2.size() && taxa1.containsAll(taxa2);
                if (isEquals) {
                    addEqualsNodesConstraints(t1, node1, t2, node2);
                    totalEquals++;
                }

                boolean allDifferent = Collections.disjoint(taxa1, taxa2);
                if (allDifferent && t1 < t2) {
                    addDifferentTaxaNodesConstraints(t1, node1, t2, node2);
                    totalDifferent++;
                }
            }
        }
        commentCNF("In trees %d and %d there are %d pairs of equal nodes and %d pairs of different nodes",
                t1, t2, totalEquals, totalDifferent);
    }

    private void addEqualsNodesConstraints(int t1, int n1, int t2, int n2) {
        PhylogeneticTree phTree1 = this.phTrees.get(t1);
        PhylogeneticTree phTree2 = this.phTrees.get(t2);
        commentCNF("Node %d in tree %d have the same %d taxons (out of %d) in subtree as node %d in tree %d",
                n1, t1, phTree1.getTaxa(n1).size(), this.n, n2, t2);


        for (int nodeNumber : treeNodes()) {
            int x1Var = getVar("x", t1, n1, nodeNumber);
            int x2Var = getVar("x", t2, n2, nodeNumber);
            addClause(-x1Var, x2Var); // x1 then x2
        }

        for (int subtreeNode : phTree1.getSubtreeNodes(n1)) {
            if (subtreeNode >= n) {
                for (int nonSubtreeNode = n; nonSubtreeNode < 2 * n - 1; nonSubtreeNode++) {
                    if (!phTree2.getSubtreeNodes(n2).contains(nonSubtreeNode)) {
                        for (int nodeNumber : treeNodes()) {
                            int x1Var = getVar("x", t1, subtreeNode, nodeNumber);
                            int x2Var = getVar("x", t2, nonSubtreeNode, nodeNumber);
                            addClause(-x1Var, -x2Var); // x1 then ~x2
                        }
                    }
                }
            }
        }
    }

    /*
    Adding this constraints is a little bit risky
     */
    private void addDifferentTaxaNodesConstraints(int t1, int n1, int t2, int n2) {
        PhylogeneticTree phTree1 = this.phTrees.get(t1);
        PhylogeneticTree phTree2 = this.phTrees.get(t2);
        commentCNF("Node %d in tree %d and node %d in tree %d have disjoint set of taxons (%d and %d)",
                n1, t1, n2, t2, phTree1.getTaxa(n1).size(), phTree2.getTaxa(n2).size());

        for (int nodeNumber : treeNodes()) {
            int x1Var = getVar("x", t1, n1, nodeNumber);
            int x2Var = getVar("x", t2, n2, nodeNumber);
            addClause(-x1Var, -x2Var);
        }
    }

    private List<Integer> possibleChildren(int nodeNumber) {
        if (nodeNumber < 0 || nodeNumber >= treeNodesCount + k) {
            throw new RuntimeException("Node number out of bounds");
        }

        List<Integer> ans = new ArrayList<Integer>();
        if (nodeNumber < n) {
            return ans;
        }
        for (int childNumber : allNodes()) {
            if (nodeNumber < treeNodesCount) {
                if (childNumber < nodeNumber || childNumber >= treeNodesCount) {
                    ans.add(childNumber);
                }
            } else if (childNumber < treeNodesCount - 1 || (enableReticulationConnection && childNumber < nodeNumber)) {
                ans.add(childNumber);
            }
        }

        return ans;
    }

    private List<Integer> possibleParents(int nodeNumber) {
        if (nodeNumber < 0 || nodeNumber >= treeNodesCount + k) {
            throw new RuntimeException("Node number out of bounds");
        }

        List<Integer> ans = new ArrayList<Integer>();
        if (nodeNumber == treeNodesCount - 1) {
            return ans;
        }
        for (int parentNumber = n; parentNumber < treeNodesCount + k; parentNumber++) {
            if (nodeNumber < n) {
                ans.add(parentNumber);
            } else if (nodeNumber < treeNodesCount) {
                if (nodeNumber < parentNumber) {
                    ans.add(parentNumber);
                }
            } else if (parentNumber < treeNodesCount || (enableReticulationConnection && nodeNumber < parentNumber)) {
                ans.add(parentNumber);
            }
        }

        return ans;
    }

    private List<Integer> possibleUp(int nodeNumber) {
        List<Integer> ans = new ArrayList<Integer>();
        for (int up : possibleParents(nodeNumber)) {
            if (up < treeNodesCount) {
                ans.add(up);
            }
        }
        return ans;
    }

    private List<Integer> allNodes() {
        List<Integer> ans = new ArrayList<Integer>();
        for (int nodeNumber = 0; nodeNumber < this.treeNodesCount + k; nodeNumber++) {
            ans.add(nodeNumber);
        }
        return ans;
    }

    private List<Integer> treeNodes() {
        List<Integer> ans = new ArrayList<Integer>();
        for (int nodeNumber = this.n; nodeNumber < this.treeNodesCount; nodeNumber++) {
            ans.add(nodeNumber);
        }
        return ans;
    }

    private List<Integer> reticulationNodes() {
        List<Integer> ans = new ArrayList<Integer>();
        for (int nodeNumber = this.treeNodesCount; nodeNumber < this.treeNodesCount + k; nodeNumber++) {
            ans.add(nodeNumber);
        }
        return ans;
    }

    private String getKey(String type, int ... params) {
        String key = type;
        for (int p : params) {
            key += "_" + p;
        }
        return key;
    }

    private int createVar(String type, int ... params) {
        m.put(getKey(type, params), m.size() + 1);
        return m.size();
    }

    private int getVar(String type, int ... params) {
        return m.get(getKey(type, params));
    }

    private void commentCNF(String format, Object... args) {
        if (!disableComments) {
            this.sb.append(String.format("c " + format + "\n", args));
        }
    }

    private void addClause(String clause) {
        if (!clause.endsWith(" ")) {
            clause += " ";
        }
        clause += "0\n";
        this.sb.append(clause);
        clausesCount++;
    }

    private void addClause(int ... literals) {
        String clause = "";
        for (int literal : literals) {
            clause += literal + " ";
        }
        addClause(clause);
    }
}