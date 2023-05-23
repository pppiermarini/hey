from artifactory_cleanup import rules
from artifactory_cleanup.rules import CleanupPolicy

RULES = [

    # ------ ALL REPOS --------
    #,rules.delete_docker_images_older_than(days=90)
    #,rules.include_path('hello-world/')
    #rules.include_path('*/**'),
    #rules.include_path('*/**'),
    #rules.keep_latest_n_file_in_folder(count=5)
    #        rules.keep_latest_version_n_file_in_folder(count=2, custom_regexp='.*\b')
    #        rules.keep_latest_n_version_images_by_property(count=2)
    #        rules.keep_latest_n_version_images_by_property(count=2, custom_regexp='.*SNAPSHOT*.'),
    #rules.keep_latest_n_version_images_by_property(count=2,custom_regexp='.*SNAPSHOT*.'),



    CleanupPolicy(
       'Running Policy',
        rules.repo('incomm-snapshot'),
        rules.include_path('*/cclp/**'),
        rules.keep_latest_n_file_in_folder(count=5)
        )

]