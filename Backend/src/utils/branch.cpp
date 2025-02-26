#include "branch.h"
#include "../function/expressions/shared/shared.h"

#include "../function/expressions/shared/shared.h"
#include "../translatetype.h"

#include <functional>

void buildIfV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen) {
  buildIf(globalState, functionState->containingFuncL, builder, conditionLE, buildThen);
}

void buildIfNever(
    GlobalState* globalState,
    LLVMValueRef funcL,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen) {
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  assert(LLVMTypeOf(conditionLE) == int1LT);

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "then" and
  // "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----------------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");

  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, afterwardBlockL);

  // Now, we fill in the "then" block.
  buildThen(thenBlockBuilder);

  // Don't add an instruction to get to the afterward block, since this part is unreachable.
  // LLVMBuildBr(thenBlockBuilder, afterwardBlockL);
  // Instead, have an unreachable instruction:
  LLVMBuildUnreachable(thenBlockBuilder);

  LLVMDisposeBuilder(thenBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.
}

void buildIfReturn(
    GlobalState* globalState,
    LLVMValueRef funcL,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen) {
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  assert(LLVMTypeOf(conditionLE) == int1LT);

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "then" and
  // "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----------------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");

  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, afterwardBlockL);

  // Now, we fill in the "then" block.
  auto toReturnLE = buildThen(thenBlockBuilder);

  LLVMBuildRet(thenBlockBuilder, toReturnLE);

  LLVMDisposeBuilder(thenBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.
}

void buildIf(
    GlobalState* globalState,
    LLVMValueRef funcL,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen) {
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  assert(LLVMTypeOf(conditionLE) == int1LT);

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "then" and
  // "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----------------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, funcL, "");

  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, afterwardBlockL);

  // Now, we fill in the "then" block.
  buildThen(thenBlockBuilder);
  // Instruction to jump to the afterward block.
  LLVMBuildBr(thenBlockBuilder, afterwardBlockL);

  LLVMDisposeBuilder(thenBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.
}

LLVMValueRef buildIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef resultTypeL,
    LLVMValueRef conditionLE,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) {

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make three more: "then",
  // "else", and "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----> else -----'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);
  // Now, we fill in the "then" block.
  auto thenResultLE = buildThen(thenBlockBuilder);
  // A builder can point to different blocks, so get the latest one so we can
  // pull from it for the phi.
  auto thenFinalBlockL = LLVMGetInsertBlock(thenBlockBuilder);


  LLVMBasicBlockRef elseStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef elseBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(elseBlockBuilder, elseStartBlockL);
  // Now, we fill in the "else" block.
  auto elseResultLE = buildElse(elseBlockBuilder);
  // A builder can point to different blocks, so get the latest one so we can
  // pull from it for the phi.
  auto elseFinalBlockL = LLVMGetInsertBlock(elseBlockBuilder);

  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, elseStartBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  // Instruction to jump to the afterward block.
  LLVMBuildBr(thenBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(thenBlockBuilder);

  // Instruction to jump to the afterward block.
  LLVMBuildBr(elseBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(elseBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // Now, we fill in the afterward block, to receive the result value of the
  // then or else block, whichever we just came from.
  auto phi = LLVMBuildPhi(builder, resultTypeL, "");
  LLVMValueRef incomingValueRefs[2] = {thenResultLE, elseResultLE};
  LLVMBasicBlockRef incomingBlocks[2] = {thenFinalBlockL, elseFinalBlockL};
  LLVMAddIncoming(phi, incomingValueRefs, incomingBlocks, 2);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.

  return phi;
}

void buildVoidIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen,
    std::function<void(LLVMBuilderRef)> buildElse) {

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make three more: "then",
  // "else", and "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----> else -----'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);
  // Now, we fill in the "then" block.
  buildThen(thenBlockBuilder);


  LLVMBasicBlockRef elseStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef elseBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(elseBlockBuilder, elseStartBlockL);
  // Now, we fill in the "else" block.
  buildElse(elseBlockBuilder);

  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, elseStartBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  // Instruction to jump to the afterward block.
  LLVMBuildBr(thenBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(thenBlockBuilder);

  // Instruction to jump to the afterward block.
  LLVMBuildBr(elseBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(elseBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.
}

Ref buildIfElseV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref conditionRef,
    Reference* thenResultMT,
    Reference* elseResultMT,
    std::function<Ref(LLVMBuilderRef)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make three more: "then",
  // "else", and "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----> else -----'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenStartBlockL);
  // Now, we fill in the "then" block.
  auto thenResultRef = buildThen(thenBlockBuilder);
  auto thenResultLE =
      globalState->getRegion(thenResultMT)
          ->checkValidReference(FL(), functionState, thenBlockBuilder, false, thenResultMT, thenResultRef);
  // A builder can point to different blocks, so get the latest one so we can
  // pull from it for the phi.
  auto thenFinalBlockL = LLVMGetInsertBlock(thenBlockBuilder);


  LLVMBasicBlockRef elseStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef elseBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(elseBlockBuilder, elseStartBlockL);
  // Now, we fill in the "else" block.
  auto elseResultRef = buildElse(elseBlockBuilder);
  auto elseResultLE =
      globalState->getRegion(elseResultMT)
          ->checkValidReference(FL(), functionState, elseBlockBuilder, false, elseResultMT, elseResultRef);
  // A builder can point to different blocks, so get the latest one so we can
  // pull from it for the phi.
  auto elseFinalBlockL = LLVMGetInsertBlock(elseBlockBuilder);

  auto conditionLE =
      globalState->getRegion(globalState->metalCache->boolRef)
          ->checkValidReference(FL(), functionState, builder, true, globalState->metalCache->boolRef, conditionRef);
  LLVMBuildCondBr(builder, conditionLE, thenStartBlockL, elseStartBlockL);

  if (thenResultMT == globalState->metalCache->neverRef && elseResultMT == globalState->metalCache->neverRef) {
    // Bail early, even though builder is still pointing at the preceding block. Nobody should use
    // it, since nothing can happen after a never.
    return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
//    assert(false); // impl
  }

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  if (thenResultMT != globalState->metalCache->neverRef) {
    // Instruction to jump to the afterward block.
    LLVMBuildBr(thenBlockBuilder, afterwardBlockL);
  }
  LLVMDisposeBuilder(thenBlockBuilder);
  if (elseResultMT != globalState->metalCache->neverRef) {
    // Instruction to jump to the afterward block.
    LLVMBuildBr(elseBlockBuilder, afterwardBlockL);
  }
  LLVMDisposeBuilder(elseBlockBuilder);
  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);


  if (thenResultMT == globalState->metalCache->neverRef) {
    return elseResultRef;
  } else if (elseResultMT == globalState->metalCache->neverRef) {
    return thenResultRef;
  } else {
    assert(LLVMTypeOf(thenResultLE) == LLVMTypeOf(elseResultLE));
    // Now, we fill in the afterward block, to receive the result value of the
    // then or else block, whichever we just came from.
    auto phi = LLVMBuildPhi(builder, LLVMTypeOf(thenResultLE), "");
    LLVMValueRef incomingValueRefs[2] = {thenResultLE, elseResultLE};
    LLVMBasicBlockRef incomingBlocks[2] = {thenFinalBlockL, elseFinalBlockL};
    LLVMAddIncoming(phi, incomingValueRefs, incomingBlocks, 2);

    // We're done with the "current" block, and also the "then" and "else"
    // blocks, nobody else will write to them now.
    // We re-pointed the `builder` to point at the "afterward" block, and
    // subsequent instructions after the if will keep adding to that.

    return wrap(globalState->getRegion(thenResultMT), thenResultMT, phi);
  }
}

void buildBoolyWhile(
    GlobalState* globalState,
    LLVMValueRef containingFuncL,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildBody) {

  // While only has a body expr, no separate condition.
  // If the body itself returns true, then we'll run the body again.

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "body" and
  // "afterward".
  //              .-----> body -----.
  //  current ---'         ↑         :---> afterward
  //                       `--------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the While) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef bodyStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, containingFuncL, "");
  LLVMBuilderRef bodyBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(bodyBlockBuilder, bodyStartBlockL);

  // Jump from our previous block into the body for the first time.
  LLVMBuildBr(builder, bodyStartBlockL);

  auto continueLE = buildBody(bodyBlockBuilder);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context, containingFuncL, "");

  LLVMBuildCondBr(bodyBlockBuilder, continueLE, bodyStartBlockL, afterwardBlockL);

  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);
}

void buildBoolyWhileV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef)> buildBody) {
  buildBoolyWhile(
      globalState, functionState->containingFuncL, builder,
      [globalState, functionState, buildBody](LLVMBuilderRef builder) {
        auto continueRef = buildBody(builder);
        auto continueLE =
            globalState->getRegion(globalState->metalCache->boolRef)->
                checkValidReference(
                    FL(), functionState, builder, true, globalState->metalCache->boolRef, continueRef);
        return continueLE;
      });
}

void buildBreakyWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<void(LLVMBuilderRef, LLVMBasicBlockRef)> buildBody) {

  // While only has a body expr, no separate condition.
  // If the body itself returns true, then we'll run the body again.

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "body" and
  // "afterward".
  //              .-----> body -----.
  //  current ---'         ↑         :---> afterward
  //                       `--------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the While) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef bodyStartBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef bodyBlockBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(bodyBlockBuilder, bodyStartBlockL);

  // Jump from our previous block into the body for the first time.
  LLVMBuildBr(builder, bodyStartBlockL);

  //buildBody(bodyBlockBuilder);
//  auto continueLE = globalState->getRegion(globalState->metalCache->boolRef)->checkValidReference(FL(), functionState, builder, globalState->metalCache->boolRef, continueRef);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlockInContext(globalState->context,
          functionState->containingFuncL,
          functionState->nextBlockName().c_str());

  buildBody(bodyBlockBuilder, afterwardBlockL);

  LLVMBuildBr(bodyBlockBuilder, bodyStartBlockL);
  //LLVMBuildCondBr(bodyBlockBuilder, continueLE, bodyStartBlockL, afterwardBlockL);

  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);
}

void buildWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef)> buildCondition,
    std::function<void(LLVMBuilderRef)> buildBody) {
  buildBoolyWhileV(
      globalState,
      functionState,
      builder,
      [globalState, functionState, buildCondition, buildBody](LLVMBuilderRef bodyBuilder) -> Ref {
        auto conditionLE = buildCondition(bodyBuilder);
        return buildIfElseV(
            globalState,
            functionState,
            bodyBuilder,
            conditionLE,
//            LLVMInt1TypeInContext(globalState->context),
            globalState->metalCache->boolRef,
            globalState->metalCache->boolRef,
            [globalState, functionState, buildBody](LLVMBuilderRef thenBlockBuilder) {
              buildBody(thenBlockBuilder);
              // Return true, so the while loop will keep executing.
              return wrap(
                  globalState->getRegion(globalState->metalCache->boolRef),
                  globalState->metalCache->boolRef,
                  makeConstIntExpr(functionState, thenBlockBuilder, LLVMInt1TypeInContext(globalState->context), 1));
            },
            [globalState, functionState](LLVMBuilderRef elseBlockBuilder) -> Ref {
              // Return false, so the while loop will stop executing.
              return wrap(
                  globalState->getRegion(globalState->metalCache->boolRef),
                  globalState->metalCache->boolRef,
                  makeConstIntExpr(functionState, elseBlockBuilder, LLVMInt1TypeInContext(globalState->context), 0));
            });
      });
}
