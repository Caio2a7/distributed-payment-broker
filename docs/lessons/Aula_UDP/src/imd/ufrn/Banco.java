package imd.ufrn;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class Banco {
	private ConcurrentHashMap<Integer,Integer> contas = new ConcurrentHashMap<Integer, Integer>();
	public void addConta(int numconta) {
		contas.put(numconta, 0);
	}
	public void depositar(int numconta, int valor) {
		int atual = contas.get(numconta);
		contas.put(numconta, atual+valor);
	}
	public int saldo(int numconta) {
		return contas.get(numconta);
	}
}
