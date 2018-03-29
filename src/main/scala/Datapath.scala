package rself

import chisel3._
import chisel3.util._

object Const {
  val PC_START = 0x200
  val PC_EVEC  = 0x100
}

class InstructionReader extends Module {
  val io = IO(new Bundle {
    val en    = Input(Bool())
    val ready = Output(Bool())

    val isJmp = Input(Bool())
    val jaddr = Input(UInt(32.W))

    val inst  = Output(UInt(32.W))
    val pc    = Output(UInt(32.W))

    val mem = Flipped(new MemoryReaderIO)
  })
  val pc  = RegInit(Const.PC_START.U(32.W))
  val npc = pc + 4.U
  val enable = Module(new Posedge)

  enable.io.in := io.en
  enable.io.re := !io.mem.vaild
  io.mem.ren := enable.io.out

  when (io.en) {
    pc := Mux(io.isJmp, io.jaddr, npc)
  }

  io.mem.addr := pc
  io.inst := io.mem.data
  io.ready := io.mem.vaild
  io.pc := pc
}

class WriteBack extends Module {
  val io = IO(new Bundle {
    val en    = Input(Bool())
    val ready = Output(Bool())

    val rd   = Input(UInt(5.W))
    val data = Input(UInt(32.W))

    val reg = Flipped(new RegFileWriterIO)
  })

  io.reg.waddr := io.rd
  io.reg.wdata := io.data

  when (io.en) {
    io.reg.wen := true.B
    io.ready := true.B
  }.otherwise {
    io.reg.wen := false.B
    io.ready := false.B
  }
}

class DatapathIO extends Bundle {
  val dmem = Flipped(new MemoryIO)
  val imem = Flipped(new MemoryReaderIO)
}

class Datapath extends Module {
  val io  = IO(new DatapathIO)
  val reg = Module(new RegFile)
  val exe = Module(new Execute)
  val inr = Module(new InstructionReader)
  val wrb = Module(new WriteBack)

  exe.io.inst := inr.io.inst
  exe.io.pc   := inr.io.pc

  inr.io.isJmp := exe.io.jmp
  inr.io.jaddr := exe.io.npc

  wrb.io.rd   := exe.io.rd
  wrb.io.data := exe.io.data

  exe.io.mem <> io.dmem
  inr.io.mem <> io.imem
  exe.io.reg <> reg.io.reader
  wrb.io.reg <> reg.io.writer

  when (wrb.io.ready | reset.toBool) {
    inr.io.en := true.B
  }.otherwise {
    inr.io.en := false.B
  }

  when (inr.io.ready) {
    exe.io.en := true.B
  }.otherwise {
    exe.io.en := false.B
  }

  when (exe.io.ready) {
    wrb.io.en := true.B
  }.otherwise {
    wrb.io.en := false.B
  }

}