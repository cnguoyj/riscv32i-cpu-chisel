import chisel3._
import bundles._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class IDTestModule extends Module {
  val io = IO(new Bundle {
    val iff = Flipped(new IF_ID())  // tester acts as ID
    val ex = new ID_EX()
    val regw = Flipped(new MEM_Reg())
  })

  val reg = Module(new RegFile())
  reg.io._MEM.addr := io.regw.addr
  reg.io._MEM.data := io.regw.data

  val id = Module(new ID())
  id.io.iff.pc := io.iff.pc
  id.io.iff.inst := io.iff.inst
  io.iff.if_branch := id.io.iff.if_branch
  io.iff.branch_tar := id.io.iff.branch_tar
  id.io.reg.read1 <> reg.io._ID.read1
  id.io.reg.read2 <> reg.io._ID.read2
  io.ex.oprd1 := id.io.ex.oprd1
  io.ex.oprd2 := id.io.ex.oprd2
  io.ex.opt := id.io.ex.opt
  io.ex.reg_w_add := id.io.ex.reg_w_add
  io.ex.store_data := id.io.ex.store_data
}


class IDTest(t: IDTestModule) extends PeekPokeTester(t) {
  // initialize register values
  for (i <- 1 until 10) {
    poke(t.io.regw.addr, i.U)
    poke(t.io.regw.data, (0x1000 + i).U)
    step(1)
  }

  val testcases = Array(
    //               inst , num1 , num2 ,              aluop , wregaddr ,  br , brtgt ,         inst
    Array("h_00A0_0093".U ,  0.U , 10.U , OptCode.ADD ,      1.U , 0.U ,  0.U) , // addi r1 r0 1
    Array("h_FAA0_8193".U ,  0x1001.U , "h_ffff_ffaa".U , OptCode.ADD ,3.U , 0.U ,  0.U), // addi r3 r1=0x1001 0xFAA=0xFFFFFFAA
    Array("h_8003_0793".U ,  0x1006.U , "h_ffff_f800".U , OptCode.ADD , 15.U , 0.U ,  0.U) //, addi r15 r6=0x1006 0x800=0xFFFFF800
  )

  for (tc <- testcases) {
    poke(t.io.iff.inst, tc(0))
    expect(t.io.ex.oprd1, tc(1))
    expect(t.io.ex.oprd2, tc(2))
    expect(t.io.ex.opt, tc(3))
    expect(t.io.ex.reg_w_add, tc(4))
    expect(t.io.iff.if_branch, tc(5))
    expect(t.io.iff.branch_tar, tc(6))
  }
}


class IDTester extends ChiselFlatSpec {
    val args = Array[String]()
    "ID module" should "pass test" in {
      iotesters.Driver.execute(args, () => new IDTestModule()) {
        c => new IDTest(c)
      } should be (true)
    }
}