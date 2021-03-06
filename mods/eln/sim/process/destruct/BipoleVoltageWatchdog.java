package mods.eln.sim.process.destruct;

import mods.eln.sim.mna.component.Bipole;
import mods.eln.sim.mna.component.Resistor;

public class BipoleVoltageWatchdog extends ValueWatchdog {
	Bipole bipole;

	public BipoleVoltageWatchdog set(Bipole bipole) {
		this.bipole = bipole;
		return this;
	}

	public BipoleVoltageWatchdog setUNominal(double UNominal) {
		this.max = UNominal;
		this.min = -UNominal;
		this.timeoutReset = UNominal * 0.10 * 5;

		return this;
	}

	@Override
	double getValue() {
		return bipole.getU();
	}
}
