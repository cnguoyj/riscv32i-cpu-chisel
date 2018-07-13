import chisel3._
import bundles._
import chisel3.util._

object Insts { // idea from mini riscv
//  // Loads
//  def LB     = BitPat("b?????????????????000?????0000011")
//  def LH     = BitPat("b?????????????????001?????0000011")
//  def LW     = BitPat("b?????????????????010?????0000011")
//  def LBU    = BitPat("b?????????????????100?????0000011")
//  def LHU    = BitPat("b?????????????????101?????0000011")
//  // Stores
//  def SB     = BitPat("b?????????????????000?????0100011")
//  def SH     = BitPat("b?????????????????001?????0100011")
//  def SW     = BitPat("b?????????????????010?????0100011")
//  // Shifts
//  def SLL    = BitPat("b0000000??????????001?????0110011")
//  def SLLI   = BitPat("b0000000??????????001?????0010011")
//  def SRL    = BitPat("b0000000??????????101?????0110011")
//  def SRLI   = BitPat("b0000000??????????101?????0010011")
//  def SRA    = BitPat("b0100000??????????101?????0110011")
//  def SRAI   = BitPat("b0100000??????????101?????0010011")
//  // Arithmetic
//  def ADD    = BitPat("b0000000??????????000?????0110011")
  def ADDI   = BitPat("b?????????????????000?????0010011")
//  def SUB    = BitPat("b0100000??????????000?????0110011")
//  def LUI    = BitPat("b?????????????????????????0110111")
//  def AUIPC  = BitPat("b?????????????????????????0010111")
//  // Logical
//  def XOR    = BitPat("b0000000??????????100?????0110011")
//  def XORI   = BitPat("b?????????????????100?????0010011")
//  def OR     = BitPat("b0000000??????????110?????0110011")
//  def ORI    = BitPat("b?????????????????110?????0010011")
//  def AND    = BitPat("b0000000??????????111?????0110011")
//  def ANDI   = BitPat("b?????????????????111?????0010011")
//  // Compare
//  def SLT    = BitPat("b0000000??????????010?????0110011")
//  def SLTI   = BitPat("b?????????????????010?????0010011")
//  def SLTU   = BitPat("b0000000??????????011?????0110011")
//  def SLTIU  = BitPat("b?????????????????011?????0010011")
//  // Branches
//  def BEQ    = BitPat("b?????????????????000?????1100011")
//  def BNE    = BitPat("b?????????????????001?????1100011")
//  def BLT    = BitPat("b?????????????????100?????1100011")
//  def BGE    = BitPat("b?????????????????101?????1100011")
//  def BLTU   = BitPat("b?????????????????110?????1100011")
//  def BGEU   = BitPat("b?????????????????111?????1100011")
//  // Jump & Link
//  def JAL    = BitPat("b?????????????????????????1101111")
//  def JALR   = BitPat("b?????????????????000?????1100111")
}

object DecTable {
  // num1_sel
  val NUM1_RS1  = 1.U(1.W)

  // num2_sel
  val NUM2_RS2   = 1.U(2.W)
  val NUM2_I_IMM = 2.U(2.W)

  // alu op
  val ALUOP_ADD = 0.U(4.W)

  // default decode signals
  val defaultDec =
            List(NUM1_RS1, NUM2_RS2,   ALUOP_ADD, false.B, true.B)
  //             num1-sel  num2-sel      aluop    wreg?    bad?
  val decMap = Array(
    Insts.ADDI -> List(NUM1_RS1, NUM2_I_IMM, ALUOP_ADD, true.B,  false.B))

  val DEC_NUM1_SEL = 0
  val DEC_NUM2_SEL = 1
  val DEC_ALUOP = 2
  val DEC_WREG = 3
  val DEC_BAD = 4
}

class ID extends Module {
  val io = IO(new Bundle {
    val iff = Flipped(new IF_ID())  // naming conflict if use `if`
    val reg = new ID_Reg()
    val ex = new ID_EX()
  })

  // branch signals wired back to IF. TODO
  io.iff.if_branch := false.B
  io.iff.branch_tar := 0.U

  // split
  val rs1Addr  = io.iff.inst(19, 15)
  val rs2Addr  = io.iff.inst(24, 20)
  val rdAddr   = io.iff.inst(11, 7)

  // read immediate + sign/zero extend
  //  FUCK CHISEL PIECE OF SHITTY CRAP
  val iImm = Wire(SInt(32.W))
  iImm := io.iff.inst(31, 20).asSInt

  // read registers
  io.reg.read1.addr := rs1Addr
  io.reg.read2.addr := rs2Addr
  val rs1val = io.reg.read1.data
  val rs2val = io.reg.read2.data
 
  // decode
  val decRes = ListLookup(io.iff.inst, DecTable.defaultDec, DecTable.decMap)
  val num1 = MuxLookup(decRes(DecTable.DEC_NUM1_SEL), 0.U(32.W), Seq(
      DecTable.NUM1_RS1 -> rs1val
  ))
  val num2 = MuxLookup(decRes(DecTable.DEC_NUM2_SEL), 0.U(32.W), Seq(
      DecTable.NUM2_RS2 -> rs2val,
      DecTable.NUM2_I_IMM -> iImm.asUInt
  ))

  // pass to alu
  io.ex.oprd1 := num1
  io.ex.oprd2 := num2
  io.ex.opt := decRes(DecTable.DEC_ALUOP)
  io.ex.reg_w_add := Mux(decRes(DecTable.DEC_WREG).toBool, rdAddr, 0.U)
  io.ex.store_data := 0.U // TODO

  // TODO: deal with bad instructions
}
