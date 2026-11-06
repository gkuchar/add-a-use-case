package team.projectpulse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PeerEvaluationUser, Integer> {

    Optional<PeerEvaluationUser> findByUsername(String username);

    Optional<PeerEvaluationUser> findByEmail(String email);

    List<PeerEvaluationUser> findByEmailIn(Collection<String> emails);

    /**
     * Whether the user's account is active, without loading the user: read on every bearer request
     * (FR-SEC-active-account). Empty for an id that names no user.
     */
    @Query("select u.enabled from PeerEvaluationUser u where u.id = :id")
    Optional<Boolean> findEnabledById(@Param("id") Integer id);

}
