package dev.vale.typing.citizen

import dev.vale.highertyping.ImplA
import dev.vale.postparsing.{IRuneS, ITemplataType, ImplImpreciseNameS, ImplSubCitizenImpreciseNameS, ImplTemplataType}
import dev.vale.postparsing.rules.{Equivalencies, IRulexSR, RuleScout}
import dev.vale.solver.{IIncompleteOrFailedSolve, SolverErrorHumanizer}
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing.env.{ExpressionLookupContext, TemplataLookupContext, TemplatasStore}
import dev.vale.typing._
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Accumulator, Err, Interner, Ok, Profiler, RangeS, Result, U, postparsing, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing._
import dev.vale.typing.ast.{CitizenDefinitionT, ImplT, InterfaceDefinitionT}
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler.EvaluateFunctionFailure
import dev.vale.typing.infer.ITypingPassSolverError

import scala.collection.immutable.Set

sealed trait IsParentResult
case class IsParent(
  templata: ITemplata[ImplTemplataType],
  conclusions: Map[IRuneS, ITemplata[ITemplataType]],
  implFullName: IdT[IImplNameT]
) extends IsParentResult
case class IsntParent(
  candidates: Vector[IIncompleteOrFailedCompilerSolve]
) extends IsParentResult

class ImplCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    nameTranslator: NameTranslator,
    structCompiler: StructCompiler,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler) {

  // We don't have an isAncestor call, see REMUIDDA.

  def solveImplForCall(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    initialKnowns: Vector[InitialKnown],
    implTemplata: ImplDefinitionTemplata,
    isRootSolve: Boolean,
    verifyConclusions: Boolean):
  ICompilerSolverOutcome = {
    val ImplDefinitionTemplata(parentEnv, impl) = implTemplata
    val ImplA(
      range,
      name,
      identifyingRunes,
      rules,
      runeToType,
      structKindRune,
      subCitizenImpreciseName,
      interfaceKindRune,
      superInterfaceImpreciseName
    ) = impl

    val implTemplateFullName =
      parentEnv.fullName.addStep(nameTranslator.translateImplName(name))

    val outerEnv =
      CitizenEnvironment(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateFullName,
        implTemplateFullName,
        TemplatasStore(implTemplateFullName, Map(), Map()))

    // Remember, impls can have rules too, such as:
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // so we do need to filter them out when compiling.
    val definitionRules = rules.filter(InferCompiler.includeRuleInCallSiteSolve)

    val result =
      inferCompiler.solve(
        InferEnv(
          // This is callingEnv because we might be coming from an abstraction function that's trying
          // to evaluate an override.
          callingEnv,
          range :: parentRanges,
          outerEnv),
        coutputs,
        definitionRules,
        runeToType,
        range :: parentRanges,
        initialKnowns,
        Vector(),
        verifyConclusions,
        isRootSolve,
        // We include the reachable bounds for the struct rune. Those are bounds that this impl will
        // have to satisfy when it calls the interface.
        Vector(structKindRune.rune))
    result
  }

  private def solveImplForDefine(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    initialKnowns: Vector[InitialKnown],
    implTemplata: ImplDefinitionTemplata,
    verifyConclusions: Boolean,
    isRootSolve: Boolean):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    val ImplDefinitionTemplata(parentEnv, impl) = implTemplata
    val ImplA(
    range,
    name,
    identifyingRunes,
    rules,
    runeToType,
    structKindRune,
    subCitizenImpreciseName,
    interfaceKindRune,
    superInterfaceImpreciseName
    ) = impl

    val implTemplateFullName =
      parentEnv.fullName.addStep(nameTranslator.translateImplName(name))

    val outerEnv =
      CitizenEnvironment(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateFullName,
        implTemplateFullName,
        TemplatasStore(implTemplateFullName, Map(), Map()))

    // Remember, impls can have rules too, such as:
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // so we do need to filter them out when compiling.
    val definitionRules = rules.filter(InferCompiler.includeRuleInDefinitionSolve)

    val result =
      inferCompiler.solveComplete(
        InferEnv(
          // This is callingEnv because we might be coming from an abstraction function that's trying
          // to evaluate an override.
          callingEnv,
          range :: parentRanges,
          outerEnv),
        coutputs,
        definitionRules,
        runeToType,
        range :: parentRanges,
        initialKnowns,
        Vector(),
        true,
        isRootSolve,
        // We include reachable bounds for the struct so we don't have to re-specify all its bounds in the impl.
        Vector(structKindRune.rune))
    result
  }

  // This will just figure out the struct template and interface template,
  // so we can add it to the temputs.
  def compileImpl(coutputs: CompilerOutputs, implTemplata: ImplDefinitionTemplata): Unit = {
    val ImplDefinitionTemplata(parentEnv, implA) = implTemplata

    val implTemplateFullName =
      parentEnv.fullName.addStep(
        nameTranslator.translateImplName(implA.name))

    val implOuterEnv =
      CitizenEnvironment(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateFullName,
        implTemplateFullName,
        TemplatasStore(implTemplateFullName, Map(), Map()))

    val implPlaceholders =
      implA.genericParams.zipWithIndex.map({ case (rune, index) =>
        val placeholder =
          templataCompiler.createPlaceholder(
            coutputs, implOuterEnv, implTemplateFullName, rune, index, implA.runeToType, true)
        InitialKnown(rune.rune, placeholder)
      })

    val CompleteCompilerSolve(_, inferences, runeToFunctionBound1, reachableBoundsFromSubCitizen) =
      solveImplForDefine(coutputs, List(implA.range), implOuterEnv, implPlaceholders, implTemplata, true, true) match {
        case Ok(i) => i
        case Err(e) => throw CompileErrorExceptionT(CouldntEvaluatImpl(List(implA.range), e))
      }

    val subCitizen =
      inferences.get(implA.subCitizenRune.rune) match {
        case None => vwat()
        case Some(KindTemplata(s: ICitizenTT)) => s
        case _ => vwat()
      }
    val subCitizenTemplateFullName =
      TemplataCompiler.getCitizenTemplate(subCitizen.fullName)

    val superInterface =
      inferences.get(implA.interfaceKindRune.rune) match {
        case None => vwat()
        case Some(KindTemplata(i@InterfaceTT(_))) => i
        case Some(other) => throw CompileErrorExceptionT(CantImplNonInterface(List(implA.range), other))
      }
    val superInterfaceTemplateFullName =
      TemplataCompiler.getInterfaceTemplate(superInterface.fullName)


    val templateArgs = implA.genericParams.map(_.rune.rune).map(inferences)
    val instantiatedFullName = assembleImplName(implTemplateFullName, templateArgs, subCitizen)

    val implInnerEnv =
      GeneralEnvironment.childOf(
        interner,
        implOuterEnv,
        instantiatedFullName,
        reachableBoundsFromSubCitizen.zipWithIndex.map({ case (templata, index) =>
          interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(templata)
        }).toVector ++
        inferences.map({ case (nameS, templata) =>
          interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
        }).toVector)
    val runeToNeededFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(implInnerEnv.templatas)
    val runeToNeededImplBound = TemplataCompiler.assembleRuneToImplBound(implInnerEnv.templatas)
//    vcurious(runeToFunctionBound1 == runeToNeededFunctionBound) // which do we want?

    val runeIndexToIndependence =
      calculateRunesIndependence(coutputs, implTemplata, implOuterEnv, superInterface)

    val implT =
      interner.intern(
        ImplT(
          implTemplata,
          implOuterEnv,
          instantiatedFullName,
          implTemplateFullName,
          subCitizenTemplateFullName,
          subCitizen,
          superInterface,
          superInterfaceTemplateFullName,
          runeToNeededFunctionBound,
          runeToNeededImplBound,
          runeIndexToIndependence.toVector,
          reachableBoundsFromSubCitizen.map(_.prototype)))
    coutputs.declareType(implTemplateFullName)
    coutputs.declareTypeOuterEnv(implTemplateFullName, implOuterEnv)
    coutputs.declareTypeInnerEnv(implTemplateFullName, implInnerEnv)
    coutputs.addImpl(implT)
  }

  def calculateRunesIndependence(
    coutputs: CompilerOutputs,
    implTemplata: ImplDefinitionTemplata,
    implOuterEnv: IEnvironment,
    interface: InterfaceTT,
  ): Vector[Boolean] = {

    // Now we're going to figure out the <ZZ> for the eg Milano case.
    val (partialCaseConclusionsFromSuperInterface, _) =
      solveImplForCall(
        coutputs,
        List(implTemplata.impl.range),
        implOuterEnv,
        Vector(
          InitialKnown(
            implTemplata.impl.interfaceKindRune,
            // We may be feeding in something interesting like IObserver<Opt<T>> here should be fine,
            // the impl will receive it and match it to its own unknown runes appropriately.
            KindTemplata(interface))),
        implTemplata,
        false,
        // Don't verify conclusions, because this will likely be a partial solve, which means we
        // might not even be able to solve the struct, which means we can't pull in any declared
        // function bounds that come from them. We'll check them later.
        false) match {
        case CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromSubCitizen) => (conclusions, reachableBoundsFromSubCitizen)
        case IncompleteCompilerSolve(_, _, _, incompleteConclusions) => (incompleteConclusions, Vector[ITemplata[ITemplataType]]())
        case fcs @ FailedCompilerSolve(_, _, _) => {
          throw CompileErrorExceptionT(CouldntEvaluatImpl(List(implTemplata.impl.range), fcs))
        }
      }
    // These will be anything that wasn't already determined by the incoming interface.
    // These are the "independent" generic params, like the <ZZ> in Milano.
    // No particular reason they're ordered, it just feels appropriate to keep them in the same
    // order they appeared in the impl.
    val runeToIndependence =
      implTemplata.impl.genericParams.map(_.rune.rune)
        .map(rune => !partialCaseConclusionsFromSuperInterface.contains(rune))

    runeToIndependence
  }

  def assembleImplName(
    templateName: IdT[IImplTemplateNameT],
    templateArgs: Vector[ITemplata[ITemplataType]],
    subCitizen: ICitizenTT):
  IdT[IImplNameT] = {
    templateName.copy(
      localName = templateName.localName.makeImplName(interner, templateArgs, subCitizen))
  }

  //    // First, figure out what citizen is implementing.
  //    val subCitizenImpreciseName = RuleScout.getRuneKindTemplate(implA.rules, implA.structKindRune.rune)
  //    val subCitizenTemplata =
  //      implOuterEnv.lookupNearestWithImpreciseName(subCitizenImpreciseName, Set(TemplataLookupContext)) match {
  //        case None => throw CompileErrorExceptionT(ImplSubCitizenNotFound(implA.range, subCitizenImpreciseName))
  //        case Some(it @ CitizenTemplata(_, _)) => it
  //        case Some(other) => throw CompileErrorExceptionT(NonCitizenCantImpl(implA.range, other))
  //      }
  //    val subCitizenTemplateFullName = templataCompiler.resolveCitizenTemplate(subCitizenTemplata)
  //    val subCitizenDefinition = coutputs.lookupCitizen(subCitizenTemplateFullName)
  //    val subCitizenPlaceholders =
  //      subCitizenDefinition.genericParamTypes.zipWithIndex.map({ case (tyype, index) =>
  //        templataCompiler.createPlaceholder(coutputs, implOuterEnv, implTemplateFullName, index, tyype)
  //      })
  //    val placeholderedSubCitizenTT =
  //      structCompiler.resolveCitizen(coutputs, implOuterEnv, implA.range, subCitizenTemplata, subCitizenPlaceholders)
  //
  //
  //    // Now, figure out what interface is being implemented.
  //    val superInterfaceImpreciseName = RuleScout.getRuneKindTemplate(implA.rules, implA.interfaceKindRune.rune)
  //    val superInterfaceTemplata =
  //      implOuterEnv.lookupNearestWithImpreciseName(superInterfaceImpreciseName, Set(TemplataLookupContext)) match {
  //        case None => throw CompileErrorExceptionT(ImplSuperInterfaceNotFound(implA.range, superInterfaceImpreciseName))
  //        case Some(it @ InterfaceTemplata(_, _)) => it
  //        case Some(other) => throw CompileErrorExceptionT(CantImplNonInterface(implA.range, other))
  //      }
  //    val superInterfaceTemplateFullName = templataCompiler.resolveCitizenTemplate(superInterfaceTemplata)
  //    val superInterfaceDefinition = coutputs.lookupCitizen(superInterfaceTemplateFullName)
  //    val superInterfacePlaceholders =
  //      superInterfaceDefinition.genericParamTypes.zipWithIndex.map({ case (tyype, index) =>
  //        val placeholderNameT = implTemplateFullName.addStep(PlaceholderNameT(PlaceholderTemplateNameT(index)))
  //        templataCompiler.createPlaceholder(coutputs, implOuterEnv, implTemplateFullName, index, tyype)
  //      })
  //    val placeholderedSuperInterfaceTT =
  //      structCompiler.resolveInterface(coutputs, implOuterEnv, implA.range, superInterfaceTemplata, superInterfacePlaceholders)
  //
  //    // Now compile it from the sub citizen's perspective.
  //    compileImplGivenSubCitizen(coutputs, placeholderedSubCitizenTT, implTemplata)
  //    // Now compile it from the super interface's perspective.
  //    compileImplGivenSuperInterface(coutputs, placeholderedSuperInterfaceTT, implTemplata)
  //  }
  //
  //  def compileParentImplsForSubCitizen(
  //    coutputs: CompilerOutputs,
  //    subCitizenDefinition: CitizenDefinitionT):
  //  Unit = {
  //    Profiler.frame(() => {
  //      val subCitizenTemplateFullName = subCitizenDefinition.templateName
  //      val subCitizenEnv = coutputs.getEnvForTemplate(subCitizenTemplateFullName)
  //      // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
  //      val needleImplTemplateFullName = interner.intern(ImplTemplateSubNameT(subCitizenTemplateFullName))
  //      val implTemplates =
  //        subCitizenEnv.lookupAllWithName(needleImplTemplateFullName, Set(TemplataLookupContext))
  //      implTemplates.foreach({
  //        case it @ ImplTemplata(_, _) => {
  //          compileImplGivenSubCitizen(coutputs, subCitizenDefinition, it)
  //        }
  //        case other => vwat(other)
  //      })
  //    })
  //  }
  //
  //  def compileChildImplsForParentInterface(
  //    coutputs: CompilerOutputs,
  //    parentInterfaceDefinition: InterfaceDefinitionT):
  //  Unit = {
  //    Profiler.frame(() => {
  //      val parentInterfaceTemplateFullName = parentInterfaceDefinition.templateName
  //      val parentInterfaceEnv = coutputs.getEnvForTemplate(parentInterfaceTemplateFullName)
  //      // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
  //      val needleImplTemplateFullName = interner.intern(ImplTemplateSuperNameT(parentInterfaceTemplateFullName))
  //      val implTemplates =
  //        parentInterfaceEnv.lookupAllWithName(needleImplTemplateFullName, Set(TemplataLookupContext))
  //      implTemplates.foreach({
  //        case impl @ ImplTemplata(_, _) => {
  //          compileImplGivenSuperInterface(coutputs, parentInterfaceDefinition, impl)
  //        }
  //        case other => vwat(other)
  //      })
  //    })
  //  }

  //  // Doesn't include self
  //  def compileGetAncestorInterfaces(
  //    coutputs: CompilerOutputs,
  //    descendantCitizenRef: ICitizenTT):
  //  (Map[InterfaceTT, ImplTemplateNameT]) = {
  //    Profiler.frame(() => {
  //      val parentInterfacesAndImpls =
  //        compileGetParentInterfaces(coutputs, descendantCitizenRef)
  //
  //      // Make a map that contains all the parent interfaces, with distance 1
  //      val foundSoFar =
  //        parentInterfacesAndImpls.map({ case (interfaceRef, impl) => (interfaceRef, impl) }).toMap
  //
  //      compileGetAncestorInterfacesInner(
  //        coutputs,
  //        foundSoFar,
  //        parentInterfacesAndImpls.toMap)
  //    })
  //  }
  //
  //  private def compileGetAncestorInterfacesInner(
  //    coutputs: CompilerOutputs,
  //    // This is so we can know what we've already searched.
  //    nearestDistanceByInterfaceRef: Map[InterfaceTT, ImplTemplateNameT],
  //    // These are the interfaces that are *exactly* currentDistance away.
  //    // We will do our searching from here.
  //    interfacesAtCurrentDistance: Map[InterfaceTT, ImplTemplateNameT]):
  //  (Map[InterfaceTT, ImplTemplateNameT]) = {
  //    val interfacesAtNextDistance =
  //      interfacesAtCurrentDistance.foldLeft((Map[InterfaceTT, ImplTemplateNameT]()))({
  //        case ((previousAncestorInterfaceRefs), (parentInterfaceRef, parentImpl)) => {
  //          val parentAncestorInterfaceRefs =
  //            compileGetParentInterfaces(coutputs, parentInterfaceRef)
  //          (previousAncestorInterfaceRefs ++ parentAncestorInterfaceRefs)
  //        }
  //      })
  //
  //    // Discard the ones that have already been found; they're actually at
  //    // a closer distance.
  //    val newlyFoundInterfaces =
  //    interfacesAtNextDistance.keySet
  //      .diff(nearestDistanceByInterfaceRef.keySet)
  //      .toVector
  //      .map(key => (key -> interfacesAtNextDistance(key)))
  //      .toMap
  //
  //    if (newlyFoundInterfaces.isEmpty) {
  //      (nearestDistanceByInterfaceRef)
  //    } else {
  //      // Combine the previously found ones with the newly found ones.
  //      val newNearestDistanceByInterfaceRef =
  //        nearestDistanceByInterfaceRef ++ newlyFoundInterfaces.toMap
  //
  //      compileGetAncestorInterfacesInner(
  //        coutputs,
  //        newNearestDistanceByInterfaceRef,
  //        newlyFoundInterfaces)
  //    }
  //  }
  //
  //  def getParents(
  //    coutputs: CompilerOutputs,
  //    subCitizenTT: ICitizenTT):
  //  Vector[InterfaceTT] = {
  //    val subCitizenTemplateFullName = TemplataCompiler.getCitizenTemplate(subCitizenTT.fullName)
  //    coutputs
  //      .getParentImplsForSubCitizenTemplate(subCitizenTemplateFullName)
  //      .map({ case ImplT(_, parentInterfaceFromPlaceholderedSubCitizen, _, _) =>
  //        val substituter =
  //          TemplataCompiler.getPlaceholderSubstituter(interner, subCitizenTT.fullName)
  //        substituter.substituteForInterface(parentInterfaceFromPlaceholderedSubCitizen)
  //      }).toVector
  //  }
  //

  def isDescendant(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    kind: ISubKindTT,
    verifyConclusions: Boolean):
  Boolean = {
    getParents(coutputs, parentRanges, callingEnv, kind, verifyConclusions).nonEmpty
  }

  def getImplDescendantGivenParent(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    implTemplata: ImplDefinitionTemplata,
    parent: InterfaceTT,
    verifyConclusions: Boolean,
    isRootSolve: Boolean):
  Result[ICitizenTT, IIncompleteOrFailedCompilerSolve] = {
    val initialKnowns =
      Vector(
        InitialKnown(implTemplata.impl.interfaceKindRune, KindTemplata(parent)))
    val CompleteCompilerSolve(_, conclusions, _, _) =
      solveImplForCall(coutputs, parentRanges, callingEnv, initialKnowns, implTemplata, isRootSolve, true) match {
        case ccs @ CompleteCompilerSolve(_, _, _, _) => ccs
        case x : IIncompleteOrFailedCompilerSolve => return Err(x)
      }
    val parentTT = conclusions.get(implTemplata.impl.subCitizenRune.rune)
    vassertSome(parentTT) match {
      case KindTemplata(i : ICitizenTT) => Ok(i)
      case _ => vwat()
    }
  }

  def getImplParentGivenSubCitizen(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    implTemplata: ImplDefinitionTemplata,
    child: ICitizenTT,
    verifyConclusions: Boolean):
  Result[InterfaceTT, IIncompleteOrFailedCompilerSolve] = {
    val initialKnowns =
      Vector(
        InitialKnown(implTemplata.impl.subCitizenRune, KindTemplata(child)))
    val childEnv =
      coutputs.getOuterEnvForType(
        parentRanges,
        TemplataCompiler.getCitizenTemplate(child.fullName))
    val CompleteCompilerSolve(_, conclusions, _, _) =
      solveImplForCall(coutputs, parentRanges, callingEnv, initialKnowns, implTemplata, false, true) match {
        case ccs @ CompleteCompilerSolve(_, _, _, _) => ccs
        case x : IIncompleteOrFailedCompilerSolve => return Err(x)
      }
    val parentTT = conclusions.get(implTemplata.impl.interfaceKindRune.rune)
    vassertSome(parentTT) match {
      case KindTemplata(i @ InterfaceTT(_)) => Ok(i)
      case _ => vwat()
    }
  }

  def getParents(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callingEnv: IEnvironment,
    subKind: ISubKindTT,
    verifyConclusions: Boolean):
  Vector[ISuperKindTT] = {
    val subKindFullName = subKind.fullName
    val subKindTemplateName = TemplataCompiler.getSubKindTemplate(subKindFullName)
    val subKindEnv = coutputs.getOuterEnvForType(parentRanges, subKindTemplateName)
    val subKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, subKindFullName.localName) match {
        case None => return Vector()
        case Some(n) => n
      }
    val implImpreciseNameS =
      interner.intern(ImplSubCitizenImpreciseNameS(subKindImpreciseName))

    val matching =
      subKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      callingEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext))

    val implDefsWithDuplicates = new Accumulator[ImplDefinitionTemplata]()
    val implTemplatasWithDuplicates = new Accumulator[IsaTemplata]()

    matching.foreach({
      case it@ImplDefinitionTemplata(_, _) => implDefsWithDuplicates.add(it)
      case it@IsaTemplata(_, _, _, _) => implTemplatasWithDuplicates.add(it)
      case _ => vwat()
    })

    val implDefs =
      implDefsWithDuplicates.buildArray().groupBy(_.impl.range).map(_._2.head)
    val parentsFromImplDefs =
      implDefs.flatMap(impl => {
        subKind match {
          case subCitizen : ICitizenTT => {
            getImplParentGivenSubCitizen(coutputs, parentRanges, callingEnv, impl, subCitizen, verifyConclusions) match {
              case Ok(x) => List(x)
              case Err(_) => {
                opts.debugOut("Throwing away error! TODO: Use an index or something instead.")
                List()
              }
            }
          }
          case _ => List()
        }
      }).toVector

    val parentsFromImplTemplatas =
      implTemplatasWithDuplicates
        .buildArray()
        .filter(_.subKind == subKind)
        .map(_.superKind)
        .collect({ case x : ISuperKindTT => x })
        .distinct

    parentsFromImplDefs ++ parentsFromImplTemplatas
  }

  def isParent(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment,
    parentRanges: List[RangeS],
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT):
  IsParentResult = {
    val superKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, superKindTT.fullName.localName) match {
        case None => return IsntParent(Vector())
        case Some(n) => n
      }
    val subKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, subKindTT.fullName.localName) match {
        case None => return IsntParent(Vector())
        case Some(n) => n
      }
    val implImpreciseNameS =
      interner.intern(ImplImpreciseNameS(subKindImpreciseName, superKindImpreciseName))

    val subKindEnv =
      coutputs.getOuterEnvForType(
        parentRanges, TemplataCompiler.getSubKindTemplate(subKindTT.fullName))
    val superKindEnv =
      coutputs.getOuterEnvForType(
        parentRanges, TemplataCompiler.getSuperKindTemplate(superKindTT.fullName))

    val matching =
      callingEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      subKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      superKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext))

    val implsDefsWithDuplicates = new Accumulator[ImplDefinitionTemplata]()
    val implTemplatasWithDuplicatesAcc = new Accumulator[IsaTemplata]()
    matching.foreach({
      case it@ImplDefinitionTemplata(_, _) => implsDefsWithDuplicates.add(it)
      case it@IsaTemplata(_, _, _, _) => implTemplatasWithDuplicatesAcc.add(it)
      case _ => vwat()
    })
    val implTemplatasWithDuplicates = implTemplatasWithDuplicatesAcc.buildArray()

    implTemplatasWithDuplicates.find(i => i.subKind == subKindTT && i.superKind == superKindTT) match {
      case Some(impl) => {
        coutputs.addInstantiationBounds(impl.implName, InstantiationBoundArguments(Map(), Map()))
        return IsParent(impl, Map(), impl.implName)
      }
      case None =>
    }

    val impls =
      implsDefsWithDuplicates.buildArray().groupBy(_.impl.range).map(_._2.head)
    val results =
      impls.map(impl => {
        val initialKnowns =
          Vector(
            InitialKnown(impl.impl.subCitizenRune, KindTemplata(subKindTT)),
            InitialKnown(impl.impl.interfaceKindRune, KindTemplata(superKindTT)))
        solveImplForCall(coutputs, parentRanges, callingEnv, initialKnowns, impl, false, true) match {
          case ccs @ CompleteCompilerSolve(_, _, _, _) => Ok((impl, ccs))
          case x : IIncompleteOrFailedCompilerSolve => Err(x)
        }
      })
    val (oks, errs) = Result.split(results)
    vcurious(oks.size <= 1)
    oks.headOption match {
      case Some((implTemplata, CompleteCompilerSolve(_, conclusions, runeToSuppliedFunction, reachableBoundsFromSubCitizen))) => {
        // Dont need this for anything yet
        val _ = reachableBoundsFromSubCitizen

        val templateArgs =
          implTemplata.impl.genericParams.map(_.rune.rune).map(conclusions)
        val implTemplateFullName =
          implTemplata.env.fullName.addStep(
            nameTranslator.translateImplName(implTemplata.impl.name))
        val instantiatedFullName = assembleImplName(implTemplateFullName, templateArgs, subKindTT.expectCitizen())
        coutputs.addInstantiationBounds(instantiatedFullName, runeToSuppliedFunction)
        IsParent(implTemplata, conclusions, instantiatedFullName)
      }
      case None => IsntParent(errs.toVector)
    }
  }
}
