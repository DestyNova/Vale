package dev.vale.typing.function

import dev.vale.{Interner, Keywords, Profiler, RangeS, vassert, vcurious, vfail, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.IFunctionDeclarationNameS
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import FunctionCompiler.IEvaluateFunctionResult
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.{AddressibleClosureVariableT, BuildingFunctionEnvironmentWithClosureds, IEnvEntry, IEnvironment, IVariableT, ReferenceClosureVariableT, TemplataEnvEntry, TemplatasStore}
import dev.vale.typing.{CompilerOutputs, ConvertHelper, InferCompiler, TemplataCompiler, TypingPassOptions, env}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, IdT, IFunctionTemplateNameT, INameT, NameTranslator}
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.{List, Map}

// When typingpassing a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionCompilerClosureOrLightLayer(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val ordinaryOrTemplatedLayer =
    new FunctionCompilerSolvingLayer(
      opts, interner, keywords, nameTranslator, templataCompiler, inferCompiler, convertHelper, structCompiler, delegate)
//
//  // This is for the early stages of Compiler when it's scanning banners to put in
//  // its env. We just want its banner, we don't want to evaluate it.
//  def predictOrdinaryLightFunctionBanner(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    function: FunctionA):
//  (FunctionBannerT) = {
//    checkNotClosure(function);
//    vassert(!function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
//    ordinaryOrTemplatedLayer.predictOrdinaryFunctionBanner(
//      newEnv, coutputs)
//  }


//  def evaluateOrdinaryLightFunctionFromNonCallForBanner(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (PrototypeTemplata) = {
//    checkNotClosure(function);
//    vassert(!function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
//
//    vcurious(function.isLambda())
//    // We dont declare the template here, we declare it when the lambda is declared.
////    coutputs.declareFunction(newEnv.fullName)
////    coutputs.declareFunctionOuterEnv(newEnv.fullName, outerEnv)
//
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
//      newEnv, coutputs, callRange, verifyConclusions)
//  }

  def evaluateTemplatedClosureFunctionFromCallForBanner(
      parentEnv: IEnvironment,
      coutputs: CompilerOutputs,
      callingEnv: IEnvironment,
      callRange: List[RangeS],
      closureStructRef: StructTT,
      function: FunctionA,
      alreadySpecifiedTemplateArgs: Vector[ITemplata[ITemplataType]],
      argTypes: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
    val name = parentEnv.fullName.addStep(nameTranslator.translateGenericTemplateFunctionName(function.name, argTypes))
//    coutputs.declareType(name)
    val outerEnv =
      BuildingFunctionEnvironmentWithClosureds(
        parentEnv.globalEnv,
        parentEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables,
        false)
//    coutputs.declareTypeOuterEnv(name, outerEnv)

    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
      outerEnv, coutputs, callingEnv, callRange, alreadySpecifiedTemplateArgs, argTypes)
  }

  def evaluateTemplatedClosureFunctionFromCallForPrototype(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment,
    callRange: List[RangeS],
    closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata[ITemplataType]],
    argTypes: Vector[CoordT],
    verifyConclusions: Boolean):
  (IEvaluateFunctionResult) = {
    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
    val name = outerEnv.fullName.addStep(nameTranslator.translateGenericTemplateFunctionName(function.name, argTypes))
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables,
        false)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, coutputs, callingEnv, callRange, alreadySpecifiedTemplateArgs, argTypes, verifyConclusions)
  }

  def evaluateTemplatedLightFunctionFromCallForPrototype2(
      parentEnv: IEnvironment,
      coutputs: CompilerOutputs,
      callingEnv: IEnvironment, // See CSSNCE
      callRange: List[RangeS],
      function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
      argTypes: Vector[CoordT],
      verifyConclusions: Boolean):
  (IEvaluateFunctionResult) = {
    checkNotClosure(function);

    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericTemplateFunctionName(function.name, argTypes))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, false)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      outerEnv, coutputs, callingEnv, callRange, explicitTemplateArgs, argTypes, verifyConclusions)
  }

  def evaluateGenericLightFunctionFromCallForPrototype2(
    parentEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    function: FunctionA,
    explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
    args: Vector[Option[CoordT]]):
  (IEvaluateFunctionResult) = {
    checkNotClosure(function);

    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericFunctionName(function.name))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, false)
    ordinaryOrTemplatedLayer.evaluateGenericFunctionFromCallForPrototype(
      outerEnv, coutputs, callingEnv, callRange, explicitTemplateArgs, args)
  }

  def evaluateGenericLightFunctionParentForPrototype2(
    parentEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    function: FunctionA,
    args: Vector[Option[CoordT]]):
  IEvaluateFunctionResult = {
    checkNotClosure(function);
    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericFunctionName(function.name))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, true)
    ordinaryOrTemplatedLayer.evaluateGenericFunctionParentForPrototype(
      outerEnv, coutputs, callingEnv, callRange, args)
  }


//  def evaluateOrdinaryLightFunctionFromNonCallForHeader(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
//    vassert(!function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
//    coutputs.declareFunction(newEnv.fullName)
//    coutputs.declareFunctionOuterEnv(newEnv.fullName, newEnv)
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
//      newEnv, coutputs, parentRanges, verifyConclusions)
//  }

  def evaluateGenericLightFunctionFromNonCall(
    parentEnv: IEnvironment,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    function: FunctionA,
    verifyConclusions: Boolean):
  (FunctionHeaderT) = {
    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericFunctionName(function.name))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, true)
    ordinaryOrTemplatedLayer.evaluateGenericFunctionFromNonCall(
      outerEnv, coutputs, parentRanges, verifyConclusions)
  }

//  def evaluateTemplatedLightFunctionFromNonCallForHeader(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
////    vassert(function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
//    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
//      newEnv, coutputs, parentRanges, verifyConclusions)
//  }

//  // We would want only the prototype instead of the entire header if, for example,
//  // we were calling the function. This is necessary for a recursive function like
//  // func main():Int{main()}
//  def evaluateOrdinaryLightFunctionFromCallForPrototype(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    callingEnv: IEnvironment, // See CSSNCE
//    callRange: List[RangeS],
//    function: FunctionA
//  ): PrototypeTemplata = {
//    checkNotClosure(function)
//    vassert(!function.isTemplate)
//
//    val name = makeNameWithClosureds(outerEnv, function.name)
//    val newEnv =
//      env.BuildingFunctionEnvironmentWithClosureds(
//        outerEnv.globalEnv,
//        outerEnv,
//        name,
//        TemplatasStore(name, Map(), Map()),
//        function,
//        Vector.empty)
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromCallForPrototype(
//      newEnv, callingEnv, coutputs, callRange)
//  }
//
//  def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    closureStructRef: StructTT,
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (PrototypeTemplata) = {
//    vassert(!function.isTemplate)
//
//    val name = makeNameWithClosureds(outerEnv, function.name)
//    coutputs.declareFunction(name)
//    coutputs.declareFunctionOuterEnv(name, outerEnv)
//    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
//    val newEnv =
//      env.BuildingFunctionEnvironmentWithClosureds(
//        outerEnv.globalEnv,
//        outerEnv,
//        name,
//        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
//        function,
//        variables)
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
//      newEnv, coutputs, callRange, verifyConclusions)
//  }
//
//  def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
//    containingEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    closureStructRef: StructTT,
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
//    // We dont here because it knows from how many variables
//    // it closures... but even lambdas without closured vars are still closures and are still
//    // backed by structs.
//    vassert(!function.isTemplate)
//
//    val name = makeNameWithClosureds(containingEnv, function.name)
//    val outerEnv = GeneralEnvironment.childOf(interner, containingEnv, name)
//
//    coutputs.declareFunction(name)
//    coutputs.declareFunctionOuterEnv(name, outerEnv)
//    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
//    val newEnv =
//      env.BuildingFunctionEnvironmentWithClosureds(
//        outerEnv.globalEnv,
//        outerEnv,
//        name,
//        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
//        function,
//        variables)
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
//      newEnv, coutputs, parentRanges, verifyConclusions)
//  }
//
//  def evaluateOrdinaryClosureFunctionFromCallForPrototype(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    callingEnv: IEnvironment, // See CSSNCE
//    closureStructRef: StructTT,
//    function: FunctionA):
//  (PrototypeTemplata) = {
//    // We dont here because it knows from how many variables
//    // it closures... but even lambdas without closured vars are still closures and are still
//    // backed by structs.
//    vassert(!function.isTemplate)
//
//    val name = makeNameWithClosureds(outerEnv, function.name)
//
//    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
//    val newEnv =
//      env.BuildingFunctionEnvironmentWithClosureds(
//        outerEnv.globalEnv,
//        outerEnv,
//        name,
//        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
//        function,
//        variables)
//    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromCallForPrototype(
//      newEnv, callingEnv, coutputs, parentRanges)
//  }
//
//  def evaluateTemplatedClosureFunctionFromNonCallForHeader(
//    outerEnv: IEnvironment,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    closureStructRef: StructTT,
//    function: FunctionA,
//    verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
//    // We dont here because it knows from how many variables
//    // it closures... but even lambdas without closured vars are still closures and are still
//    // backed by structs.
//    vassert(!function.isTemplate)
//
//    val name = makeNameWithClosureds(outerEnv, function.name)
//    coutputs.declareFunction(parentRanges, name)
//    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
//    val newEnv =
//      env.BuildingFunctionEnvironmentWithClosureds(
//        outerEnv.globalEnv,
//        outerEnv,
//        name,
//        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
//        function,
//        variables)
//    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
//      newEnv, coutputs, parentRanges, verifyConclusions)
//  }

  // This is called while we're trying to figure out what function1s to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      parentEnv: IEnvironment,
      coutputs: CompilerOutputs,
      callingEnv: IEnvironment, // See CSSNCE
      callRange: List[RangeS],
      function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
      argTypes: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    checkNotClosure(function)
    vassert(function.isTemplate)

    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericTemplateFunctionName(function.name, argTypes))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, false)
    ordinaryOrTemplatedLayer.evaluateTemplatedLightBannerFromCall(
        outerEnv, coutputs, callingEnv, callRange, explicitTemplateArgs, argTypes)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
      parentEnv: IEnvironment,
      coutputs: CompilerOutputs,
      callingEnv: IEnvironment, // See CSSNCE
      function: FunctionA,
      callRange: List[RangeS],
      alreadySpecifiedTemplateArgs: Vector[ITemplata[ITemplataType]],
      argTypes: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    vassert(function.isTemplate)
    val outerEnvFullName = parentEnv.fullName.addStep(nameTranslator.translateGenericFunctionName(function.name))
    val outerEnv = makeEnvWithoutClosureStuff(parentEnv, function, outerEnvFullName, false)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
        outerEnv, coutputs, callingEnv, callRange, alreadySpecifiedTemplateArgs, argTypes)
  }

  private def makeEnvWithoutClosureStuff(
    outerEnv: IEnvironment,
    function: FunctionA,
    name: IdT[IFunctionTemplateNameT],
    isRootCompilingDenizen: Boolean
  ): BuildingFunctionEnvironmentWithClosureds = {
    env.BuildingFunctionEnvironmentWithClosureds(
      outerEnv.globalEnv,
      outerEnv,
      name,
      TemplatasStore(name, Map(), Map()),
      function,
      Vector.empty,
      isRootCompilingDenizen)
  }

  private def checkNotClosure(function: FunctionA) = {
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case ExternBodyS =>
      case GeneratedBodyS(_) =>
      case AbstractBodyS =>
      case _ => vfail()
    }
  }

  private def makeClosureVariablesAndEntries(coutputs: CompilerOutputs, closureStructRef: StructTT):
  (Vector[IVariableT], Vector[(INameT, IEnvEntry)]) = {
    val closureStructDef = coutputs.lookupStruct(closureStructRef.fullName);
    val substituter =
      TemplataCompiler.getPlaceholderSubstituter(
        interner, keywords,
        closureStructRef.fullName,
        // This is a parameter, so we can grab bounds from it.
        InheritBoundsFromTypeItself)
    val variables =
      closureStructDef.members.map(member => {
        val variableFullName = closureStructDef.instantiatedCitizen.fullName.addStep(member.name)
        member match {
          case NormalStructMemberT(name, variability, ReferenceMemberTypeT(reference)) => {
            ReferenceClosureVariableT(
              variableFullName, closureStructRef, variability, substituter.substituteForCoord(coutputs, reference))
          }
          case NormalStructMemberT(name, variability, AddressMemberTypeT(reference)) => {
            AddressibleClosureVariableT(
              variableFullName, closureStructRef, variability, substituter.substituteForCoord(coutputs, reference))
          }
          case VariadicStructMemberT(name, tyype) => vimpl()
        }
      })
    val entries =
      Vector[(INameT, IEnvEntry)](
        closureStructRef.fullName.localName ->
          TemplataEnvEntry(KindTemplata(closureStructRef)))
    (variables, entries)
  }
}
