package mods.eln.sixnode.electricalrelay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.swing.text.MaskFormatter;


import mods.eln.Eln;
import mods.eln.item.LampDescriptor;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.IThermalDestructorDescriptor;
import mods.eln.node.IVoltageDestructorDescriptor;
import mods.eln.node.NodeBase;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElement;
import mods.eln.node.six.SixNodeElementInventory;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ElectricalResistorHeatThermalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.component.ResistorSwitch;
import mods.eln.sim.nbt.NbtElectricalGateInput;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.sim.nbt.NbtThermalLoad;
import mods.eln.sim.process.destruct.ResistorCurrentWatchdog;
import mods.eln.sim.process.destruct.ResistorPowerWatchdog;
import mods.eln.sim.process.destruct.VoltageStateWatchDog;
import mods.eln.sim.process.destruct.WorldExplosion;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import mods.eln.sixnode.lampsocket.LampSocketContainer;
import mods.eln.sound.SoundCommand;
import mods.eln.sound.SoundServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ElectricalRelayElement extends SixNodeElement {

	public ElectricalRelayElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
		super(sixNode, side, descriptor);

    	this.descriptor = (ElectricalRelayDescriptor) descriptor;
    	
		front = LRDU.Left;
    	electricalLoadList.add(aLoad);
    	electricalLoadList.add(bLoad);
    	electricalComponentList.add(switchResistor);
    	slowProcessList.add(gateProcess);
    	electricalLoadList.add(gate);
    	
    	slowProcessList.add(currentWatchDog);
    	slowProcessList.add(voltageWatchDogA);
    	slowProcessList.add(voltageWatchDogB);
    	
    	WorldExplosion exp = new WorldExplosion(this).cableExplosion();
    	
    	currentWatchDog.set(switchResistor).setIAbsMax(this.descriptor.cable.electricalMaximalCurrent).set(exp);
    	voltageWatchDogA.set(aLoad).setUNominal(this.descriptor.cable.electricalNominalVoltage).set(exp);
    	voltageWatchDogB.set(bLoad).setUNominal(this.descriptor.cable.electricalNominalVoltage).set(exp);
	}

	public ElectricalRelayDescriptor descriptor;
	public NbtElectricalLoad aLoad = new NbtElectricalLoad("aLoad");
	public NbtElectricalLoad bLoad = new NbtElectricalLoad("bLoad");
	public Resistor switchResistor = new Resistor(aLoad, bLoad);
	public NbtElectricalGateInput gate = new NbtElectricalGateInput("gate",true);
	public ElectricalRelayGateProcess gateProcess = new ElectricalRelayGateProcess(this, "GP", gate);
	
	VoltageStateWatchDog voltageWatchDogA = new VoltageStateWatchDog();
	VoltageStateWatchDog voltageWatchDogB = new VoltageStateWatchDog();
	ResistorCurrentWatchdog currentWatchDog = new ResistorCurrentWatchdog();

	

	public static boolean canBePlacedOnSide(Direction side, int type) {
		return true;
	}
	
	boolean switchState = false, defaultOutput = false;

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
        switchState = nbt.getBoolean("switchState");
        defaultOutput = nbt.getBoolean("defaultOutput");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setByte("front", (byte)((front.toInt() << 0)));
		nbt.setBoolean("switchState", switchState);
		nbt.setBoolean("defaultOutput", defaultOutput);
	}

	@Override
	public ElectricalLoad getElectricalLoad(LRDU lrdu) {
		if(front.left() == lrdu) return aLoad;
		if(front.right() == lrdu) return bLoad;
		if(front == lrdu) return gate;
		return null;
	}

	@Override
	public ThermalLoad getThermalLoad(LRDU lrdu) {
		return null;
	}

	@Override
	public int getConnectionMask(LRDU lrdu) {
		if(front.left() == lrdu) return descriptor.cable.getNodeMask();
		if(front.right() == lrdu) return descriptor.cable.getNodeMask();
		if(front == lrdu) return NodeBase.maskElectricalInputGate;
		return 0;
	}

	@Override
	public String multiMeterString() {
		return Utils.plotVolt("Ua:", aLoad.getU()) + Utils.plotVolt("Ub:", bLoad.getU()) + Utils.plotAmpere("I:", aLoad.getCurrent());
	}

	@Override
	public String thermoMeterString() {
		return "";
	}

	@Override
	public void networkSerialize(DataOutputStream stream) {
		super.networkSerialize(stream);
		try {
			stream.writeBoolean(switchState);
			stream.writeBoolean(defaultOutput);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setSwitchState(boolean state) {
		if(state == switchState) return;
		switchState = state;
		refreshSwitchResistor();
		play(new SoundCommand("random.click").mulVolume(0.1F, 2.0F).smallRange());
		needPublish(); 
	}
	
	public void refreshSwitchResistor() {
		if(switchState == false) {
			switchResistor.highImpedance();
		}
		else {
			descriptor.applyTo(switchResistor);
		}
	}
	
	public boolean getSwitchState() {
		return switchState;
	}
	
	@Override
	public void initialize() {
    	computeElectricalLoad();
    	
    	setSwitchState(switchState);
    	refreshSwitchResistor();
	}

	@Override
	protected void inventoryChanged() {
		computeElectricalLoad();
	}
	
	public ElectricalCableDescriptor cableDescriptor = null;
	
	public void computeElectricalLoad() {
		descriptor.applyTo(aLoad);
		descriptor.applyTo(bLoad);		
		refreshSwitchResistor();
	}
	
	@Override
	public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
		ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();
		
		if(Utils.isPlayerUsingWrench(entityPlayer)) {
			front = front.getNextClockwise();
			sixNode.reconnect();
			sixNode.setNeedPublish(true);
			return true;	
		}
    	return false;
	}

	public static final byte toogleOutputDefaultId = 3;
	
	@Override
	public void networkUnserialize(DataInputStream stream) {
		super.networkUnserialize(stream);
		try {
			switch(stream.readByte()) {
			case toogleOutputDefaultId:
				defaultOutput = ! defaultOutput;
				needPublish();
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean hasGui() {
		return true;
	}
}
