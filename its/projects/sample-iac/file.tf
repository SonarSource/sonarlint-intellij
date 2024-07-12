resource "aws_s3_bucket" "mynoncompliantbucket" {
  bucket = "mybucketname"

  tags = {
    "anycompany:cost-center" = "Accounting"
  }
}
