import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UKTRSubmissionRepositorySpec extends AnyFlatSpec with Matchers {

  "UKTRSubmissionRepository" should "save a submission successfully" in {
    // Test case for saving a submission
    val repository = new UKTRSubmissionRepository()
    val submission = Submission("data")
    repository.save(submission) shouldEqual true
  }

  it should "retrieve a submission by ID" in {
    // Test case for retrieving a submission
    val repository = new UKTRSubmissionRepository()
    val submission = Submission("data")
    repository.save(submission)
    repository.findById(submission.id) shouldEqual Some(submission)
  }

  it should "return None for a non-existent submission" in {
    // Test case for retrieving a non-existent submission
    val repository = new UKTRSubmissionRepository()
    repository.findById("non-existent-id") shouldEqual None
  }

  it should "delete a submission successfully" in {
    // Test case for deleting a submission
    val repository = new UKTRSubmissionRepository()
    val submission = Submission("data")
    repository.save(submission)
    repository.delete(submission.id) shouldEqual true
    repository.findById(submission.id) shouldEqual None
  }
}