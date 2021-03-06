package mods.eln.sixnode.thermalsensor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mods.eln.Eln;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElement;
import mods.eln.node.six.SixNodeElementInventory;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.sim.nbt.NbtThermalLoad;
import mods.eln.sixnode.thermalcable.ThermalCableDescriptor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


public class ThermalSensorElement extends SixNodeElement {

	public ThermalSensorElement(SixNode sixNode, Direction side,
			SixNodeDescriptor descriptor) {
		super(sixNode, side, descriptor);
		front = LRDU.Left;
    	thermalLoadList.add(thermalLoad);
    	electricalLoadList.add(outputGate);
    	electricalComponentList.add(outputGateProcess);
    	thermalProcessList.add(slowProcess);

    	this.descriptor = (ThermalSensorDescriptor) descriptor;
	}


	public ThermalSensorDescriptor descriptor;
	public NbtThermalLoad thermalLoad = new NbtThermalLoad("thermalLoad");
	public NbtElectricalLoad outputGate = new NbtElectricalLoad("outputGate");
	
	public NbtElectricalGateOutputProcess outputGateProcess = new NbtElectricalGateOutputProcess("outputGateProcess",outputGate);
	public ThermalSensorProcess slowProcess = new ThermalSensorProcess(this);
	
	
	SixNodeElementInventory inventory = new SixNodeElementInventory(1,64,this);
	LRDU front;

	public SixNodeElementInventory getInventory() {
		return inventory;
	}

	public static boolean canBePlacedOnSide(Direction side,int type)
	{
		return true;
	}
	
	static final byte powerType = 0,temperatureType = 1;
	int typeOfSensor = temperatureType; 
	float lowValue = 0,highValue = 50;


	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		
		super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value>>0) & 0x3);
        typeOfSensor = nbt.getByte("typeOfSensor");
        lowValue = nbt.getFloat("lowValue");
        highValue = nbt.getFloat("highValue");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		
		super.writeToNBT(nbt);
		nbt.setByte("front",(byte) ((front.toInt()<<0)));
		nbt.setByte("typeOfSensor", (byte) typeOfSensor);
		nbt.setFloat("lowValue", lowValue);
		nbt.setFloat("highValue", highValue);
	}

	@Override
	public ElectricalLoad getElectricalLoad(LRDU lrdu) {
		

		if(front == lrdu) return outputGate;
		
		return null;
	}

	@Override
	public ThermalLoad getThermalLoad(LRDU lrdu) {
		
		if(descriptor.temperatureOnly ==false)
		{
			if(inventory.getStackInSlot(ThermalSensorContainer.cableSlotId) != null){
				if(front.left() == lrdu) return thermalLoad;
				if(front.right() == lrdu) return thermalLoad;
			}
		}
		else
		{
			if(front.inverse() == lrdu) return thermalLoad;
		}
		return  null;
	}

	@Override
	public int getConnectionMask(LRDU lrdu) {
		if(descriptor.temperatureOnly==false)
		{
			if(inventory.getStackInSlot(ThermalSensorContainer.cableSlotId) != null){
				if(front.left() == lrdu) return NodeBase.maskThermal;
				if(front.right() == lrdu) return NodeBase.maskThermal;
			}
			if(front == lrdu) return  NodeBase.maskElectricalOutputGate;
		}
		else
		{
			if(inventory.getStackInSlot(ThermalSensorContainer.cableSlotId) != null){
				if(front.inverse() == lrdu) return NodeBase.maskThermal;
			}
			if(front == lrdu) return  NodeBase.maskElectricalOutputGate;
		}
		return 0;
	}

	@Override
	public String multiMeterString() {
		
		return "";//Utils.plotUIP(electricalLoad.Uc, electricalLoad.getCurrent());
	}

	@Override
	public String thermoMeterString() {
		
		return Utils.plotCelsius("T :", thermalLoad.Tc);
	}


	@Override
	public void networkSerialize(DataOutputStream stream) {
		
		super.networkSerialize(stream);
		try {
			stream.writeByte( (front.toInt()<<4) + typeOfSensor);
			stream.writeFloat(lowValue);
			stream.writeFloat(highValue);
			Utils.serialiseItemStack(stream,inventory.getStackInSlot(ThermalSensorContainer.cableSlotId));
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}







	@Override
	public void initialize() {
		
		Eln.instance.signalCableDescriptor.applyTo(outputGate);
    	computeElectricalLoad();
	}

	@Override
	protected void inventoryChanged() {
		computeElectricalLoad();
		reconnect();
	}

	public void computeElectricalLoad()
	{
		ItemStack cable = inventory.getStackInSlot(ThermalSensorContainer.cableSlotId);
		
		
		ThermalCableDescriptor cableDescriptor = (ThermalCableDescriptor) Eln.sixNodeItem.getDescriptor(cable);
		if(cableDescriptor == null)
		{
			thermalLoad.setHighImpedance();
		}
		else
		{
			cableDescriptor.setThermalLoad(thermalLoad);
		}

	}
	@Override
	public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side,float vx,float vy,float vz)
	{
		ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();
		
		if(Utils.isPlayerUsingWrench(entityPlayer))
		{
			front = front.getNextClockwise();
			sixNode.reconnect();
			sixNode.setNeedPublish(true);
			return true;	
		}
		else if(Eln.multiMeterElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem()))
    	{ 
    		return false;
    	}
    	if(Eln.thermoMeterElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem()))
    	{ 
    		return false;
    	}
    	if(Eln.allMeterElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem()))
    	{
    		return false;
    	}    
    	else
		{
			//setSwitchState(true);
			//return true;
		}
		//front = LRDU.fromInt((front.toInt()+1)&3);
    	return false;

	}


	public static final byte setTypeOfSensorId = 1;
	public static final byte setValueId = 2;

	@Override
	public void networkUnserialize(DataInputStream stream) {
		
		super.networkUnserialize(stream);
		try {
			switch(stream.readByte())
			{
			case setTypeOfSensorId:
				typeOfSensor = stream.readByte();
				needPublish();
				break;
			case setValueId:
				lowValue = stream.readFloat();
				highValue = stream.readFloat();
				if(lowValue == highValue) highValue += 0.0001;
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
	
	@Override
	public Container newContainer(Direction side, EntityPlayer player) {
		
		return new ThermalSensorContainer(player, inventory);
	}
	
	
	
}
