package com.fttranscendence.learning.student;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends Repository<StudentProfile, Long> {

  <S extends StudentProfile> S save(S studentProfile);

  Optional<StudentProfile> findById(Long id);

  Optional<StudentProfile> findByIdAndTutorId(Long id, Long tutorId);

  Optional<StudentProfile> findByLoginUserId(Long loginUserId);

  @Query(
      """
        select profile
        from StudentProfile profile
        left join fetch profile.memberships
        where profile.loginUserId in :loginUserIds
        """)
  List<StudentProfile> findAllByLoginUserIdInWithMemberships(
      @Param("loginUserIds") List<Long> loginUserIds);

  List<StudentProfile> findAllByTutorIdOrderByFullNameAsc(Long tutorId);

  @Query(
      """
        select distinct profile
        from StudentProfile profile
        join profile.memberships membership
        where profile.tutorId = :tutorId
          and membership.tutorId = :tutorId
          and membership.classId = :classId
        order by profile.fullName asc, profile.id asc
        """)
  List<StudentProfile> findAllByTutorIdAndClassIdOrderByFullNameAsc(
      @Param("tutorId") Long tutorId, @Param("classId") Long classId);

  boolean existsByLoginUserId(Long loginUserId);
}
