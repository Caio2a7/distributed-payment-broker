package ufrn.imd.br;

import java.util.StringTokenizer;

public class ProcessPayload {
	private Banco banco;
	public ProcessPayload(Banco bank) {
		this.banco = bank;
	}
	public String processData(String msg, String Ip) {
		
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		String operacao =null;
		int conta = 0;
		int valor = 0;
		
		StringTokenizer tokenizer = new StringTokenizer(msg, ";");
		 while (tokenizer.hasMoreElements()) {
		        operacao = tokenizer.nextToken();
		        conta = Integer.parseInt(tokenizer.nextToken());
		        valor = Integer.parseInt(tokenizer.nextToken().trim());
		  }
		String reply = "Operacao realizada:" + operacao + "-" + Ip;
		String resultadoOp = reply;
		switch (operacao) {
		case "criar":
			banco.addConta(conta);
			break;
		case "depositar":
			banco.depositar(conta, valor);
			break;
		case "saldo":
        	resultadoOp = "Saldo é R$"+banco.saldo(conta);
        	break;
		}
		
		System.out.println(resultadoOp);
		return resultadoOp;
	}
}
