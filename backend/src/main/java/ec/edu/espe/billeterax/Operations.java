package ec.edu.espe.billeterax;


import org.springframework.data.jpa.repository.JpaRepository;
public interface Operations extends JpaRepository<WalletOperation,String> {}
