package epic.trees
/*
 Copyright 2012 David Hall

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
import java.io.File
import breeze.config.Help

/**
 * Represents a treebank with attendant spans, binarization, etc. Used in all the parser trainers.
 *
 * @author dlwh
 */

@Help(text="Parameters for reading and processing a treebank.")
case class ProcessedTreebank(@Help(text="Location of the treebank directory")
                             path: File,
                             @Help(text="Max length for training sentences")
                             maxLength: Int = 10000,
                             @Help(text="Should we add the dev set for training, do this only for final test.")
                             includeDevInTrain: Boolean = false,
                             @Help(text="What kind of binarization to do. Options: left, right, head. Head is best.")
                             binarization: String = "head",
                             treebankType: String = "penn",
                             numSentences: Int = Int.MaxValue,
                             keepUnaryChainsFromTrain: Boolean = true) {

  lazy val treebank = treebankType.toLowerCase() match {
    case "penn" => Treebank.fromPennTreebankDir(path)
    case "chinese" => Treebank.fromChineseTreebankDir(path)
    case "negra" => Treebank.fromGermanTreebank(path)
    case "simple" => new SimpleTreebank(new File(path, "train.txt"), new File(path, "dev.txt"), new File(path, "test.txt"))
    case "conllonto" => Treebank.fromOntonotesDirectory(path)
    case "spmrl" =>
      var trainPath: File = new File(path, "train")
      if(!trainPath.exists)
        trainPath = new File(path, "train5k")
      val train = trainPath.listFiles().filter(_.getName.endsWith("ptb"))
      val dev = new File(path, "dev").listFiles().filter(_.getName.endsWith("ptb"))
      val test = new File(path, "test").listFiles().filter(_.getName.endsWith("ptb"))
      new SimpleTreebank(train, dev, test)
    case "spmrl5k" =>
      val train = new File(path, "train5k").listFiles().filter(_.getName.endsWith("ptb"))
      val dev = new File(path, "dev").listFiles().filter(_.getName.endsWith("ptb"))
      val test = new File(path, "test").listFiles().filter(_.getName.endsWith("ptb"))
      new SimpleTreebank(train, dev, test)
    case _ => throw new RuntimeException("Unknown Treebank type")
  }

  lazy val trainTrees: IndexedSeq[TreeInstance[AnnotatedLabel, String]] = transformTrees(treebank.train, maxLength, collapseUnaries = true).take(numSentences)
  lazy val devTrees = transformTrees(treebank.dev, 100000)
  lazy val testTrees = transformTrees(treebank.test, 1000000)


  def transformTrees(portion: treebank.Portion, maxL: Int, collapseUnaries: Boolean = false): IndexedSeq[TreeInstance[AnnotatedLabel, String]] = {
    val binarizedAndTransformed = for (
      ((tree, words), index) <- portion.trees.zipWithIndex if words.length <= maxL
    ) yield {
      val name = s"${portion.name}-$index"
      makeTreeInstance(name, tree, words, collapseUnaries)
    }

    binarizedAndTransformed.toIndexedSeq
  }


  def makeTreeInstance(name: String, tree: Tree[String], words: IndexedSeq[String], collapseUnaries: Boolean): TreeInstance[AnnotatedLabel, String] = {
    var transformed = process(tree)
    if (collapseUnaries) {
      transformed = UnaryChainCollapser.collapseUnaryChains(transformed, keepChains = keepUnaryChainsFromTrain)
    }
    TreeInstance(name, transformed, words)
  }

  def headRules = {
    binarization match {
      case "xbar" | "right" => HeadFinder.right[String]
      case "leftXbar" | "left" => HeadFinder.left[String]
      case "head" => HeadFinder.collins
      case _ => HeadFinder.collins
    }
  }

  val process: StandardTreeProcessor = new StandardTreeProcessor(headRules)
}



