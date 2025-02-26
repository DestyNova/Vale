package dev.vale.parsing.rules

import dev.vale.{Collector, StrI, parsing}
import dev.vale.parsing.TestParseUtils
import dev.vale.parsing.ast.{BorrowP, BuiltinCallPR, EqualsPR, IRulexPR, ImmutableP, InlineP, LocationPT, LocationTypePR, MutabilityPT, MutabilityTypePR, MutableP, NameOrRunePT, NameP, OwnP, OwnershipPT, OwnershipTypePR, ShareP, TemplexPR, TypedPR, WeakP, YonderP}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.parsing._
import dev.vale.parsing.ast._
import org.scalatest.{FunSuite, Matchers}

class RulesEnumsTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compileRulex(code)
//    compile(new TemplexParser().parseRule(_), code)
  }

  test("Ownership") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, StrI("X")))) => }
    compile("X Ownership") shouldHave { case TypedPR(_,Some(NameP(_, StrI("X"))),OwnershipTypePR) => }
    compile("X = own") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, StrI("X")))),TemplexPR(OwnershipPT(_,OwnP))) => }
    compile("X Ownership = any(own, borrow, weak)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, StrI("X"))),OwnershipTypePR),
          BuiltinCallPR(_,NameP(_, StrI("any")),Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,BorrowP)), TemplexPR(OwnershipPT(_,WeakP))))) =>
    }
    compile("_ Ownership") shouldHave { case TypedPR(_,None,OwnershipTypePR) => }
    compile("own") shouldHave { case TemplexPR(OwnershipPT(_,OwnP)) => }
    compile("_ Ownership = any(own, share)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,OwnershipTypePR),
          BuiltinCallPR(_,NameP(_, StrI("any")),Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,ShareP))))) =>
    }
  }

  test("Mutability") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, StrI("X")))) => }
    compile("X Mutability") shouldHave { case TypedPR(_,Some(NameP(_, StrI("X"))),MutabilityTypePR) => }
    compile("X = mut") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, StrI("X")))),TemplexPR(MutabilityPT(_,MutableP))) => }
    compile("X Mutability = mut") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, StrI("X"))),MutabilityTypePR),
          TemplexPR(MutabilityPT(_,MutableP))) =>
    }
    compile("_ Mutability") shouldHave { case TypedPR(_,None,MutabilityTypePR) => }
    compile("mut") shouldHave { case TemplexPR(MutabilityPT(_,MutableP)) => }
    compile("_ Mutability = any(mut, imm)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,MutabilityTypePR),
          BuiltinCallPR(_,NameP(_, StrI("any")),Vector(TemplexPR(MutabilityPT(_,MutableP)), TemplexPR(MutabilityPT(_,ImmutableP))))) =>
    }
  }

  test("Location") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, StrI("X")))) => }
    compile("X Location") shouldHave { case TypedPR(_,Some(NameP(_, StrI("X"))),LocationTypePR) => }
    compile("X = inl") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, StrI("X")))),TemplexPR(LocationPT(_,InlineP))) => }
    compile("X Location = inl") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, StrI("X"))),LocationTypePR),
          TemplexPR(LocationPT(_,InlineP))) =>
    }
    compile("_ Location") shouldHave { case TypedPR(_,None,LocationTypePR) => }
    compile("inl") shouldHave { case TemplexPR(LocationPT(_,InlineP)) => }
    compile("_ Location = any(inl, heap)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,LocationTypePR),
          BuiltinCallPR(_,NameP(_, StrI("any")),Vector(TemplexPR(LocationPT(_,InlineP)), TemplexPR(LocationPT(_,YonderP))))) =>
    }
  }
}
