# PatentFlow BE EKS Deployment

## Flow

```text
main push
-> GitHub Actions builds a Docker image
-> GitHub Actions pushes the image to Harbor with the commit SHA tag
-> GitHub Actions connects to EKS with kubeconfig
-> GitHub Actions applies PostgreSQL manifests in the team namespace
-> GitHub Actions applies Kubernetes manifests
-> GitHub Actions updates the Deployment image tag
-> EKS pulls the new image from Harbor and rolls out new Pods
```

Harbor stores images. It does not update EKS by itself. The deployment update is triggered by GitHub Actions through `kubectl`.

## GitHub Secrets

Set these secrets in the GitHub repository:

```text
HARBOR_REGISTRY
HARBOR_PROJECT
HARBOR_USERNAME
HARBOR_PASSWORD
KUBE_CONFIG
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
SPRING_DATASOURCE_PASSWORD
PATENTFLOW_JWT_SECRET
PATENTFLOW_BOOTSTRAP_ADMIN_USERNAME
PATENTFLOW_BOOTSTRAP_ADMIN_PASSWORD
PATENTFLOW_BOOTSTRAP_ADMIN_DISPLAY_NAME
PATENTFLOW_BOOTSTRAP_BUSINESS_USERNAME
PATENTFLOW_BOOTSTRAP_BUSINESS_PASSWORD
PATENTFLOW_BOOTSTRAP_BUSINESS_DEPARTMENT_ID
PATENTFLOW_BOOTSTRAP_BUSINESS_DISPLAY_NAME
```

Optional secrets:

```text
AWS_SESSION_TOKEN
GMAIL_USERNAME
GMAIL_APP_PASSWORD
PATENTFLOW_KIPRIS_SERVICE_KEY
```

`KUBE_CONFIG` must be base64-encoded:

```bash
base64 -i ~/.kube/config | pbcopy
```

Repository variables can override defaults:

```text
KUBE_NAMESPACE=patentflow
BE_INGRESS_HOST=api.patentflow.example.com
BE_ACM_CERTIFICATE_ARN=arn:aws:acm:ap-northeast-2:<account-id>:certificate/<certificate-id>
AWS_REGION=ap-northeast-2
AWS_PROFILE_NAME=skala-student
PATENTFLOW_CORS_ALLOWED_ORIGINS=https://patentflow.live
SPRING_PROFILES_ACTIVE=demo
SPRING_DATASOURCE_URL=jdbc:postgresql://team11-patentflow-postgres-svc:5432/patentflow?currentSchema=patentflow
SPRING_DATASOURCE_USERNAME=patentflow
PATENTFLOW_AGENT_URL=http://team11-patentflow-agent-svc:8000
```

## One-time cluster setup

If Harbor is private, the workflow creates or updates `harbor-regcred` in the namespace. The app secret and PostgreSQL secret are also applied from GitHub Secrets.

The backend Ingress uses AWS Load Balancer Controller with `ingressClassName=alb`. Do not route this service through the shared `public-nginx` class. The ALB name is `team11-patentflow-be`, and `BE_INGRESS_HOST` should be pointed to the ALB DNS name after the Ingress is provisioned. HTTPS requires an ACM certificate in `ap-northeast-2`; set `BE_ACM_CERTIFICATE_ARN` after DNS validation is complete.

The default setup deploys PostgreSQL in the same namespace as the backend:

```text
KUBE_NAMESPACE=skala3-finalproj-class3-team11
SPRING_DATASOURCE_URL=jdbc:postgresql://team11-patentflow-postgres-svc:5432/patentflow?currentSchema=patentflow
```

PostgreSQL is exposed only as a `ClusterIP` service inside the cluster. Do not create an Ingress for the database.
