package core_

import chisel3._
import chisel3.util._
import OptCode._

class EX extends Module {
  val io = IO(new Bundle {
    val id  = Flipped(new ID_EX)
    val mem = new EX_MEM
    val idWrRegOp = Flipped(new WrRegOp)
    val wrRegOp = new WrRegOp
    val idWrCSROp = Flipped(new WrCSROp)
    val wrCSROp = new WrCSROp

    //exception
    val idExcep = Flipped(new ExcepStatus)
    val excep  = new ExcepStatus

    val csrExcepEn = Input(Bool())

  })
  
  val excepEn = RegInit(false.B)
  val excepCode = RegInit(0.U(32.W))
  val excepPc = RegInit(0.U(32.W))
  excepEn   := io.idExcep.en
  excepCode := io.idExcep.code
  excepPc   := io.idExcep.pc 

  val flush = io.csrExcepEn

  val a = RegInit(0.U(32.W))
  a := io.id.oprd1
  val b = RegInit(0.U(32.W))
  b := io.id.oprd2
  val opt = RegInit(OptCode.ADD)
  opt := io.id.opt



  val shamt = b(4, 0)

  // NOTICE: SLL,SRL,SRA only use lower 5 bits of b
  val aluRes = MuxLookup(opt,
    a + b,
    Seq(
      ADD -> (a + b),
      SUB -> (a - b),
      SLT -> Mux(a.asSInt < b.asSInt, 1.U, 0.U),
      SLTU -> Mux(a < b, 1.U, 0.U),
      XOR -> (a ^ b),
      OR -> (a | b),
      AND -> (a & b),
      SLL -> (a << shamt),
      SRL -> (a >> shamt),
      SRA -> (a.asSInt >> shamt).asUInt
      // not necessary, all rest (a+b)
    )
  )
  io.mem.alu_out := aluRes

  val wregAddr = RegInit(0.U(5.W))
  wregAddr := io.idWrRegOp.addr
  io.wrRegOp.addr := wregAddr
  io.wrRegOp.data := aluRes
  io.wrRegOp.rdy := (opt & OptCode.LW) =/= OptCode.LW

  io.mem.opt       := opt
  val store_data = RegInit(0.U(32.W))
  store_data := io.id.store_data
  io.mem.store_data := store_data

  val wCSRAddr  = RegInit(0.U(12.W))
  val csrMode   = RegInit(0.U(2.W))
  val csrOldVal = RegInit(0.U(32.W))
  val csrRsVal  = RegInit(0.U(32.W))
  val csrNewVal = RegInit(0.U(32.W))

  wCSRAddr  := io.idWrCSROp.addr
  csrMode   := io.idWrCSROp.mode
  csrOldVal := io.idWrCSROp.oldVal
  csrRsVal  := io.idWrCSROp.rsVal
  csrNewVal := io.idWrCSROp.newVal
  
  io.wrCSROp.addr   := wCSRAddr
  io.wrCSROp.oldVal := csrOldVal
  io.wrCSROp.rsVal  := csrRsVal
  io.wrCSROp.mode   := csrMode
  io.wrCSROp.newVal := MuxLookup(csrMode, 0.U, Seq(
    CSRMODE.RW -> csrRsVal,
    CSRMODE.RS -> (csrOldVal | csrRsVal),
    CSRMODE.RC -> (csrOldVal & ~csrRsVal)
  ))

  io.excep.en   := excepEn
  io.excep.code := excepCode
  io.excep.pc   := excepPc
  
  when(flush) {
    opt := OptCode.ADD
    wregAddr := 0.U
    csrMode := 0.U
    excepEn := false.B
    
  }

}
