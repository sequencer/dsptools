// See LICENSE for license details.

package ipxact

import chisel3._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{RegField, RegFieldDesc, RegReadFn, RegWriteFn}

class GCD extends Module {
  val io = IO(new Bundle {
    val a  = Input(UInt(32.W))
    val b  = Input(UInt(32.W))
    val e  = Input(Bool())
    val z  = Output(UInt(32.W))
    val v  = Output(Bool())
  })
  val x = Reg(UInt(32.W))
  val y = Reg(UInt(32.W))
  when (x > y)   { x := x -% y }
    .otherwise     { y := y -% x }
  when (io.e) { x := io.a; y := io.b }
  io.z := x
  io.v := y === 0.U
}

class AXI4GCD extends LazyModule()(Parameters.empty) {

  val regs = AXI4RegisterNode(AddressSet(0x0, 0xFFFF), beatBytes = 4, concurrency = 1)

  val ioMemNode = BundleBridgeSource(() => AXI4Bundle(AXI4BundleParameters(addrBits = 8, dataBits = 32, idBits = 1)))

  regs :=
    BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
    ioMemNode

  val ioMem = InModuleBody { ioMemNode.makeIO() }

  lazy val module = new LazyModuleImp(this) {
    val a = Reg(UInt(32.W))
    val b = Reg(UInt(32.W))

    val gcd = Module(new GCD())

    regs.regmap(
      0x0 -> Seq(RegField(4, a, RegFieldDesc(name = "a", desc = "First term in GCD"))),
      0x1 -> Seq(RegField(4, b, RegFieldDesc(name = "b", desc = "Second term in GCD"))),
      0x2 -> Seq(RegField(4,
        RegReadFn((ivalid: Bool, oready: Bool) => (true.B, true.B, gcd.io.v)),
        RegWriteFn((ivalid: Bool, oready: Bool, data: UInt) => {
          gcd.io.e := ivalid && oready
          (true.B, true.B)
        })
      )),
      0x3 -> Seq(RegField(4, gcd.io.z, RegFieldDesc(name = "z", desc = "Output of GCD"))),
    )
  }
}

object PrintMe extends App {
  val dut = LazyModule(new AXI4GCD)
  println(chisel3.Driver.emit(() => dut.module))
}